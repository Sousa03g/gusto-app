import { describe, it, expect } from 'vitest';
import {
  CATEGORY_TRANSLATIONS,
  CATEGORY_REVERSE_MAP,
  translateCategory,
  translateIngredientName,
  translateUnit,
  parseMeasure,
  mapMealDbToGusto,
} from '@/lib/mealdb';

describe('MealDB Normalizer Service - Integração e Transformação de Dados', () => {
  describe('Tradução de Categorias Culinárias', () => {
    it('deve traduzir categorias em inglês para português brasileiro', () => {
      expect(translateCategory('Beef')).toBe('Carne Bovina');
      expect(translateCategory('Chicken')).toBe('Frango');
      expect(translateCategory('Dessert')).toBe('Sobremesas');
      expect(translateCategory('Pasta')).toBe('Massas');
      expect(translateCategory('Vegetarian')).toBe('Vegetariano');
    });

    it('deve manter a categoria original se não houver tradução cadastrada', () => {
      expect(translateCategory('Molecular Gastronomy')).toBe('Molecular Gastronomy');
    });

    it('deve possuir mapa reverso consistente (pt -> en)', () => {
      expect(CATEGORY_REVERSE_MAP['massas']).toBe('Pasta');
      expect(CATEGORY_REVERSE_MAP['sobremesas']).toBe('Dessert');
      expect(CATEGORY_REVERSE_MAP['frango']).toBe('Chicken');
    });
  });

  describe('Tradução e Normalização de Ingredientes', () => {
    it('deve traduzir ingredientes comuns com inicial maiúscula', () => {
      expect(translateIngredientName('garlic')).toBe('Alho');
      expect(translateIngredientName('olive oil')).toBe('Azeite de oliva');
      expect(translateIngredientName('tomato')).toBe('Tomate');
      expect(translateIngredientName('chicken breast')).toBe('Peito de frango');
    });

    it('deve capitalizar nome de ingrediente não catalogado', () => {
      expect(translateIngredientName('exotic dragonfruit')).toBe('Exotic dragonfruit');
    });
  });

  describe('Parser de Unidades e Medidas Culinárias', () => {
    it('deve converter frações simples (ex: 1/2 cup) em números decimais e unidade traduzida', () => {
      const res = parseMeasure('1/2 cup');
      expect(res.quantity).toBe(0.5);
      expect(res.unit).toBe('xícaras');
    });

    it('deve converter frações compostas (ex: 1 1/2 tbsp)', () => {
      const res = parseMeasure('1 1/2 tbsp');
      expect(res.quantity).toBe(1.5);
      expect(res.unit).toBe('colheres de sopa');
    });

    it('deve converter medidas inteiras com unidades comuns (ex: 2 cloves)', () => {
      const res = parseMeasure('2 cloves');
      expect(res.quantity).toBe(2);
      expect(res.unit).toBe('dentes');
    });

    it('deve converter medidas em gramas (ex: 250g)', () => {
      const res = parseMeasure('250g');
      expect(res.quantity).toBe(250);
      expect(res.unit).toBe('g');
    });

    it('deve assumir quantidade 1 e unidade padrão para medidas textuais sem número', () => {
      const res = parseMeasure('to taste');
      expect(res.quantity).toBe(1);
      expect(res.unit).toBe('a gosto');
    });
  });

  describe('Mapeamento Completo de Receita Externa (TheMealDB para Gusto)', () => {
    const rawMealDbMock = {
      idMeal: '52772',
      strMeal: 'Teriyaki Chicken Casserole',
      strCategory: 'Chicken',
      strArea: 'Japanese',
      strInstructions:
        'Preheat oven to 350° F.\r\nSpray a 9x13-inch baking dish with non-stick cooking spray.\r\nCombine sauce ingredients and stir until dissolved.',
      strMealThumb: 'https://www.themealdb.com/images/media/meals/wvpsxx1468256321.jpg',
      strIngredient1: 'soy sauce',
      strMeasure1: '3/4 cup',
      strIngredient2: 'water',
      strMeasure2: '1/2 cup',
      strIngredient3: 'brown sugar',
      strMeasure3: '1/4 cup',
      strIngredient4: '',
      strMeasure4: '',
    };

    it('deve converter payload cru em objeto normalizado GustoNormalizedRecipe', () => {
      const normalized = mapMealDbToGusto(rawMealDbMock);

      expect(normalized.id).toBe('mealdb-52772');
      expect(normalized.externalId).toBe('52772');
      expect(normalized.title).toBe('Teriyaki Chicken Casserole');
      expect(normalized.category).toBe('Frango');
      expect(normalized.source).toBe('THE_MEAL_DB');
      expect(normalized.imageUrl).toBe(rawMealDbMock.strMealThumb);

      // Ingredientes normalizados
      expect(normalized.ingredients.length).toBe(3);
      expect(normalized.ingredients[0]).toEqual({
        name: 'Molho shoyu',
        quantity: 0.8, // 3/4 = 0.75 arredondado para 0.8
        unit: 'xícaras',
      });
      expect(normalized.ingredients[1]).toEqual({
        name: 'Água',
        quantity: 0.5,
        unit: 'xícaras',
      });
      expect(normalized.ingredients[2]).toEqual({
        name: 'Açúcar mascavo',
        quantity: 0.3,
        unit: 'xícaras',
      });

      // Passos divididos e sequenciados
      expect(normalized.steps.length).toBe(3);
      expect(normalized.steps[0].orderNumber).toBe(1);
      expect(normalized.steps[0].instruction).toContain('Preheat oven');
      expect(normalized.steps[1].orderNumber).toBe(2);
      expect(normalized.steps[2].orderNumber).toBe(3);
    });
  });
});
