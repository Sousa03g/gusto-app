import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { prisma } from '@/lib/prisma';
import { verifyAccessToken } from '@/lib/auth';
import { searchTheMealDb, GustoNormalizedRecipe } from '@/lib/mealdb';

const recipeIngredientSchema = z.object({
  name: z.string().min(1, 'Nome do ingrediente obrigatório'),
  quantity: z.number().positive('Quantidade deve ser positiva'),
  unit: z.string().min(1, 'Unidade obrigatória'),
});

const recipeStepSchema = z.object({
  orderNumber: z.number().int().positive(),
  instruction: z.string().min(3, 'Instrução muito curta'),
});

const createRecipeSchema = z.object({
  title: z.string().min(3, 'Título deve ter no mínimo 3 caracteres').max(150),
  description: z.string().optional(),
  prepTimeMin: z.number().int().min(1, 'Tempo mínimo de 1 minuto'),
  servings: z.number().int().min(1).default(1),
  category: z.string().min(2, 'Categoria obrigatória'),
  imageUrl: z.string().url('URL de imagem inválida').optional().or(z.literal('')),
  ingredients: z.array(recipeIngredientSchema).min(1, 'Ao menos um ingrediente é obrigatório'),
  steps: z.array(recipeStepSchema).min(1, 'Ao menos um passo é obrigatório'),
});

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const search = searchParams.get('search') || '';
    const category = searchParams.get('category');
    const maxPrepTime = searchParams.get('maxPrepTime') ? parseInt(searchParams.get('maxPrepTime')!) : undefined;
    const includeExternal = searchParams.get('includeExternal') !== 'false';
    const page = Math.max(1, parseInt(searchParams.get('page') || '1'));
    const limit = Math.min(50, Math.max(1, parseInt(searchParams.get('limit') || '15')));
    const skip = (page - 1) * limit;

    // Filter database recipes
    const where: any = { isPublic: true };
    if (search) {
      where.OR = [
        { title: { contains: search, mode: 'insensitive' } },
        { description: { contains: search, mode: 'insensitive' } },
        { ingredients: { some: { name: { contains: search, mode: 'insensitive' } } } },
      ];
    }
    if (category && category !== 'Todos') {
      where.category = { equals: category, mode: 'insensitive' };
    }
    if (maxPrepTime) {
      where.prepTimeMin = { lte: maxPrepTime };
    }

    const [dbRecipes, totalDbCount] = await Promise.all([
      prisma.recipe.findMany({
        where,
        include: {
          ingredients: true,
          steps: { orderBy: { orderNumber: 'asc' } },
          user: { select: { id: true, name: true } },
        },
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      }),
      prisma.recipe.count({ where }),
    ]);

    // Format local recipes to canonical Gusto schema
    const formattedDbRecipes = dbRecipes.map(r => ({
      id: r.id,
      title: r.title,
      description: r.description,
      prepTimeMin: r.prepTimeMin,
      servings: r.servings,
      category: r.category,
      imageUrl: r.imageUrl,
      source: r.source,
      author: r.user.name,
      createdAt: r.createdAt.toISOString(),
      ingredients: r.ingredients.map(i => ({ name: i.name, quantity: i.quantity, unit: i.unit })),
      steps: r.steps.map(s => ({ orderNumber: s.orderNumber, instruction: s.instruction })),
    }));

    // If external recipes are requested (e.g. discovery feed), fetch and integrate TheMealDB
    let combined: (GustoNormalizedRecipe | typeof formattedDbRecipes[0])[] = formattedDbRecipes;
    if (includeExternal && page === 1) {
      const externalRecipes = await searchTheMealDb(search);
      const filteredExternal = category && category !== 'Todos'
        ? externalRecipes.filter(r => r.category.toLowerCase().includes(category.toLowerCase()))
        : externalRecipes;

      combined = [...formattedDbRecipes, ...filteredExternal];
    }

    return NextResponse.json({
      data: combined,
      meta: {
        page,
        limit,
        totalLocal: totalDbCount,
        hasMore: formattedDbRecipes.length === limit,
      },
    });
  } catch (error) {
    console.error('Fetch recipes error:', error);
    return NextResponse.json({ error: 'Erro ao buscar receitas' }, { status: 500 });
  }
}

export async function POST(req: NextRequest) {
  try {
    const authHeader = req.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ error: 'Não autorizado. Token de acesso obrigatório.' }, { status: 401 });
    }

    const token = authHeader.split(' ')[1];
    const userPayload = verifyAccessToken(token);
    if (!userPayload) {
      return NextResponse.json({ error: 'Token inválido ou expirado.' }, { status: 401 });
    }

    const body = await req.json();
    const validated = createRecipeSchema.safeParse(body);

    if (!validated.success) {
      return NextResponse.json(
        { error: 'Dados da receita inválidos', details: validated.error.flatten().fieldErrors },
        { status: 400 }
      );
    }

    const { title, description, prepTimeMin, servings, category, imageUrl, ingredients, steps } = validated.data;

    const newRecipe = await prisma.recipe.create({
      data: {
        title,
        description,
        prepTimeMin,
        servings,
        category,
        imageUrl: imageUrl || null,
        userId: userPayload.userId,
        source: 'GUSTO_USER',
        ingredients: {
          create: ingredients.map(ing => ({
            name: ing.name,
            quantity: ing.quantity,
            unit: ing.unit,
          })),
        },
        steps: {
          create: steps.map((st, idx) => ({
            orderNumber: st.orderNumber || idx + 1,
            instruction: st.instruction,
          })),
        },
      },
      include: {
        ingredients: true,
        steps: { orderBy: { orderNumber: 'asc' } },
        user: { select: { id: true, name: true } },
      },
    });

    return NextResponse.json(newRecipe, { status: 201 });
  } catch (error) {
    console.error('Create recipe error:', error);
    return NextResponse.json({ error: 'Erro interno ao salvar receita' }, { status: 500 });
  }
}
