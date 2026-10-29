import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GET as getRecipesHandler, POST as createRecipeHandler } from '@/app/api/recipes/route';
import { prisma } from '@/lib/prisma';
import { generateAccessToken } from '@/lib/auth';

vi.mock('@/lib/prisma', () => ({
  prisma: {
    recipe: {
      findMany: vi.fn(),
      count: vi.fn(),
      create: vi.fn(),
    },
  },
}));

vi.mock('@/lib/mealdb', async () => {
  const actual = await vi.importActual<any>('@/lib/mealdb');
  return {
    ...actual,
    searchTheMealDb: vi.fn().mockResolvedValue([
      {
        id: 'mealdb-mock-1',
        title: 'Massa Carbonara Externa',
        description: 'Receita importada',
        prepTimeMin: 25,
        servings: 2,
        category: 'Massas',
        imageUrl: null,
        source: 'THE_MEAL_DB',
        author: 'Chef TheMealDB',
        ingredients: [{ name: 'Massa', quantity: 200, unit: 'g' }],
        steps: [{ orderNumber: 1, instruction: 'Cozinhe a massa' }],
      },
    ]),
  };
});

function createMockApiRequest(url: string, method: string = 'GET', body: any = null, token?: string): any {
  return {
    url,
    method,
    headers: {
      get: (header: string) => {
        if (header.toLowerCase() === 'authorization' && token) return `Bearer ${token}`;
        if (header.toLowerCase() === 'content-type') return 'application/json';
        return null;
      },
    },
    json: async () => body,
  };
}

describe('API de Receitas - Catálogo Unificado e Criação', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('GET /api/recipes', () => {
    it('deve listar catálogo unificado (banco local + catálogo TheMealDB)', async () => {
      vi.mocked(prisma.recipe.findMany).mockResolvedValue([
        {
          id: 'local-recipe-1',
          title: 'Risoto de Funghi Local',
          description: 'Delicioso risoto',
          prepTimeMin: 40,
          servings: 4,
          category: 'Vegetariano',
          imageUrl: 'https://exemplo.com/risoto.jpg',
          source: 'GUSTO_USER',
          createdAt: new Date(),
          ingredients: [{ name: 'Arroz arbóreo', quantity: 300, unit: 'g' }],
          steps: [{ orderNumber: 1, instruction: 'Hidrate os cogumelos' }],
          user: { id: 'u1', name: 'Chef Maria' },
        } as any,
      ]);
      vi.mocked(prisma.recipe.count).mockResolvedValue(1);

      const req = createMockApiRequest('http://localhost:3000/api/recipes?page=1&limit=10');
      const res = await getRecipesHandler(req);
      const data = await res.json();

      expect(res.status).toBe(200);
      expect(data.data.length).toBeGreaterThanOrEqual(1);
      expect(data.meta.totalItems).toBeGreaterThanOrEqual(2); // 1 local + 1 externo mockado
      expect(data.data.some((r: any) => r.source === 'GUSTO_USER')).toBe(true);
      expect(data.data.some((r: any) => r.source === 'THE_MEAL_DB')).toBe(true);
    });
  });

  describe('POST /api/recipes (Criação de Receita do Usuário)', () => {
    it('deve rejeitar criação sem token de autorização (401)', async () => {
      const req = createMockApiRequest('http://localhost:3000/api/recipes', 'POST', { title: 'Receita' });
      const res = await createRecipeHandler(req);
      const data = await res.json();

      expect(res.status).toBe(401);
      expect(data.error).toContain('Não autorizado');
    });

    it('deve rejeitar payload com validações violadas (400) mesmo autenticado', async () => {
      const validToken = generateAccessToken({ userId: 'u123', email: 'autor@gusto.app' });
      const invalidPayload = {
        title: 'AB', // Mínimo 3 caracteres
        prepTimeMin: -5, // Deve ser positivo >= 1
        ingredients: [], // Mínimo 1 ingrediente
        steps: [], // Mínimo 1 passo
      };

      const req = createMockApiRequest('http://localhost:3000/api/recipes', 'POST', invalidPayload, validToken);
      const res = await createRecipeHandler(req);
      const data = await res.json();

      expect(res.status).toBe(400);
      expect(data.error).toBe('Dados da receita inválidos');
      expect(data.details).toBeDefined();
    });

    it('deve criar nova receita com sucesso quando autenticado e payload for válido (201)', async () => {
      const validToken = generateAccessToken({ userId: 'u123', email: 'autor@gusto.app' });
      const validPayload = {
        title: 'Bolo de Cenoura com Chocolate',
        description: 'Clássico com cobertura crocante',
        prepTimeMin: 50,
        servings: 8,
        category: 'Sobremesas',
        imageUrl: 'https://exemplo.com/bolo.jpg',
        ingredients: [
          { name: 'Cenouras', quantity: 3, unit: 'unidades' },
          { name: 'Farinha de trigo', quantity: 2, unit: 'xícaras' },
        ],
        steps: [
          { orderNumber: 1, instruction: 'Bata as cenouras no liquidificador com óleo e ovos' },
          { orderNumber: 2, instruction: 'Misture com a farinha e asse a 180 graus por 40 minutos' },
        ],
      };

      vi.mocked(prisma.recipe.create).mockResolvedValue({
        id: 'new-recipe-id',
        ...validPayload,
        userId: 'u123',
        source: 'GUSTO_USER',
        createdAt: new Date(),
        ingredients: validPayload.ingredients,
        steps: validPayload.steps,
        user: { id: 'u123', name: 'Chef Confeiteiro' },
      } as any);

      const req = createMockApiRequest('http://localhost:3000/api/recipes', 'POST', validPayload, validToken);
      const res = await createRecipeHandler(req);
      const data = await res.json();

      expect(res.status).toBe(201);
      expect(data.id).toBe('new-recipe-id');
      expect(data.title).toBe('Bolo de Cenoura com Chocolate');
      expect(prisma.recipe.create).toHaveBeenCalled();
    });
  });
});
