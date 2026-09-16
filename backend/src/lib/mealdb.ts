export interface GustoNormalizedIngredient {
  name: string;
  quantity: number;
  unit: string;
}

export interface GustoNormalizedRecipe {
  id: string;
  title: string;
  description: string | null;
  prepTimeMin: number;
  servings: number;
  category: string;
  imageUrl: string | null;
  source: 'THE_MEAL_DB' | 'GUSTO_USER';
  author?: string;
  createdAt?: string;
  externalId?: string;
  ingredients: GustoNormalizedIngredient[];
  steps: { orderNumber: number; instruction: string }[];
}

const THE_MEAL_DB_BASE_URL = process.env.THE_MEAL_DB_API_URL || 'https://www.themealdb.com/api/json/v1/1';

// Mapeamento de categorias Inglês <-> Português
export const CATEGORY_TRANSLATIONS: Record<string, string> = {
  'Beef': 'Carne Bovina',
  'Chicken': 'Frango',
  'Dessert': 'Sobremesas',
  'Lamb': 'Cordeiro',
  'Miscellaneous': 'Variados',
  'Pasta': 'Massas',
  'Pork': 'Carne Suína',
  'Seafood': 'Frutos do Mar',
  'Side': 'Acompanhamentos',
  'Starter': 'Entradas',
  'Vegan': 'Vegano',
  'Vegetarian': 'Vegetariano',
  'Breakfast': 'Café da Manhã',
  'Goat': 'Caprino',
};

// Mapa reverso para converter buscas em PT para EN
export const CATEGORY_REVERSE_MAP: Record<string, string> = Object.entries(CATEGORY_TRANSLATIONS).reduce(
  (acc, [en, pt]) => {
    acc[pt.toLowerCase()] = en;
    return acc;
  },
  {} as Record<string, string>
);

// Tradução de unidades de medida
const UNIT_TRANSLATIONS: Record<string, string> = {
  'tbsp': 'colheres de sopa',
  'tablespoon': 'colheres de sopa',
  'tablespoons': 'colheres de sopa',
  'tbs': 'colheres de sopa',
  'tsp': 'colheres de chá',
  'teaspoon': 'colheres de chá',
  'teaspoons': 'colheres de chá',
  'cup': 'xícaras',
  'cups': 'xícaras',
  'clove': 'dentes',
  'cloves': 'dentes',
  'pinch': 'pitada',
  'pinches': 'pitadas',
  'slice': 'fatias',
  'slices': 'fatias',
  'can': 'latas',
  'cans': 'latas',
  'tin': 'latas',
  'tins': 'latas',
  'bunch': 'maço',
  'bunches': 'maços',
  'handful': 'punhado',
  'handfuls': 'punhados',
  'piece': 'unidades',
  'pieces': 'unidades',
  'gram': 'g',
  'grams': 'g',
  'g': 'g',
  'kg': 'kg',
  'kilo': 'kg',
  'kilogram': 'kg',
  'ml': 'ml',
  'milliliter': 'ml',
  'l': 'litros',
  'liter': 'litros',
  'liters': 'litros',
  'sprig': 'ramos',
  'sprigs': 'ramos',
  'stalk': 'talos',
  'stalks': 'talos',
  'head': 'cabeça',
  'package': 'pacote',
  'packet': 'pacote',
  'drizzle': 'fio a gosto',
  'to taste': 'a gosto',
};

// Tradução de ingredientes frequentes
const INGREDIENT_TRANSLATIONS: Record<string, string> = {
  'garlic': 'Alho',
  'clove garlic': 'Dente de alho',
  'onion': 'Cebola',
  'red onion': 'Cebola roxa',
  'spring onion': 'Cebolinha',
  'shallots': 'Echalotas',
  'olive oil': 'Azeite de oliva',
  'extra virgin olive oil': 'Azeite extravirgem',
  'vegetable oil': 'Óleo vegetal',
  'butter': 'Manteiga',
  'salt': 'Sal',
  'black pepper': 'Pimenta-do-reino',
  'pepper': 'Pimenta',
  'sugar': 'Açúcar',
  'brown sugar': 'Açúcar mascavo',
  'flour': 'Farinha de trigo',
  'all-purpose flour': 'Farinha de trigo',
  'egg': 'Ovo',
  'eggs': 'Ovos',
  'egg white': 'Clara de ovo',
  'egg yolk': 'Gema de ovo',
  'milk': 'Leite',
  'water': 'Água',
  'heavy cream': 'Creme de leite fresco',
  'sour cream': 'Sour cream',
  'parmesan': 'Queijo parmesão',
  'parmesan cheese': 'Queijo parmesão ralado',
  'cheddar cheese': 'Queijo cheddar',
  'mozzarella': 'Muçarela',
  'chicken': 'Frango',
  'chicken breast': 'Peito de frango',
  'chicken thighs': 'Sobrecoxas de frango',
  'chicken stock': 'Caldo de frango',
  'chicken broth': 'Caldo de galinha',
  'beef': 'Carne bovina',
  'ground beef': 'Carne moída',
  'beef stock': 'Caldo de carne',
  'tomato': 'Tomate',
  'tomatoes': 'Tomates',
  'cherry tomatoes': 'Tomates-cereja',
  'tomato paste': 'Extrato de tomate',
  'tomato sauce': 'Molho de tomate',
  'tomato puree': 'Polpa de tomate',
  'lemon': 'Limão siciliano',
  'lemon juice': 'Suco de limão',
  'lime': 'Limão taiti',
  'lime juice': 'Suco de limão',
  'basil': 'Manjericão fresco',
  'parsley': 'Salsinha',
  'fresh parsley': 'Salsinha fresca',
  'cilantro': 'Coentro',
  'coriander': 'Coentro',
  'oregano': 'Orégano',
  'thyme': 'Tomilho',
  'rosemary': 'Alecrim',
  'cinnamon': 'Canela',
  'paprika': 'Páprica',
  'smoked paprika': 'Páprica defumada',
  'cumin': 'Cominho',
  'chili powder': 'Pimenta em pó',
  'red pepper flakes': 'Pimenta calabresa',
  'soy sauce': 'Molho shoyu',
  'ginger': 'Gengibre fresco',
  'rice': 'Arroz',
  'pasta': 'Massa',
  'spaghetti': 'Espaguete',
  'penne': 'Penne',
  'potato': 'Batata',
  'potatoes': 'Batatas',
  'carrot': 'Cenoura',
  'carrots': 'Cenouras',
  'mushroom': 'Cogumelo',
  'mushrooms': 'Cogumelos',
  'bread': 'Pão',
  'honey': 'Mel',
  'vanilla': 'Baunilha',
  'vanilla extract': 'Extrato de baunilha',
  'baking powder': 'Fermento em pó químico',
  'baking soda': 'Bicarbonato de sódio',
  'chocolate': 'Chocolate',
  'cocoa powder': 'Cacau em pó',
};

// Cache de memória para evitar sobrecarregar o TheMealDB
let cachedRecipesPool: GustoNormalizedRecipe[] | null = null;
let lastCacheTime = 0;
const CACHE_TTL_MS = 1000 * 60 * 60; // 1 hora

/**
 * Normaliza e traduz ingrediente para PT-BR
 */
function translateIngredientName(name: string): string {
  const clean = name.trim().toLowerCase();
  if (INGREDIENT_TRANSLATIONS[clean]) {
    return INGREDIENT_TRANSLATIONS[clean];
  }
  for (const [en, pt] of Object.entries(INGREDIENT_TRANSLATIONS)) {
    if (clean.includes(en)) {
      return pt;
    }
  }
  return name.charAt(0).toUpperCase() + name.slice(1);
}

/**
 * Normaliza e traduz unidade de medida para PT-BR
 */
function translateUnit(unitStr: string): string {
  const clean = unitStr.trim().toLowerCase();
  if (UNIT_TRANSLATIONS[clean]) {
    return UNIT_TRANSLATIONS[clean];
  }
  for (const [en, pt] of Object.entries(UNIT_TRANSLATIONS)) {
    if (clean.startsWith(en) || clean === en) {
      return pt;
    }
  }
  return unitStr;
}

/**
 * Traduz categoria para PT-BR
 */
export function translateCategory(cat: string): string {
  return CATEGORY_TRANSLATIONS[cat] || cat;
}

/**
 * Puxa catálogo amplo de receitas do TheMealDB
 */
export async function getAllTheMealDbRecipes(): Promise<GustoNormalizedRecipe[]> {
  const now = Date.now();
  if (cachedRecipesPool && now - lastCacheTime < CACHE_TTL_MS) {
    return cachedRecipesPool;
  }

  try {
    // Buscar um conjunto representativo de letras e termos para montar catálogo diversificado
    const queryPool = ['chicken', 'pasta', 'beef', 'dessert', 'seafood', 'vegetarian', 'breakfast', 'soup', 'salad'];
    const letterPool = ['a', 'b', 'c', 'p', 's', 'm'];

    const fetches = [
      ...queryPool.map(q => `${THE_MEAL_DB_BASE_URL}/search.php?s=${q}`),
      ...letterPool.map(l => `${THE_MEAL_DB_BASE_URL}/search.php?f=${l}`),
    ];

    const results = await Promise.allSettled(
      fetches.map(url =>
        fetch(url)
          .then(res => (res.ok ? res.json() : { meals: [] }))
          .catch(() => ({ meals: [] }))
      )
    );

    const mealMap = new Map<string, GustoNormalizedRecipe>();

    for (const res of results) {
      if (res.status === 'fulfilled' && res.value?.meals && Array.isArray(res.value.meals)) {
        for (const meal of res.value.meals) {
          if (meal && meal.idMeal && !mealMap.has(meal.idMeal)) {
            mealMap.set(meal.idMeal, mapMealDbToGusto(meal));
          }
        }
      }
    }

    const recipes = Array.from(mealMap.values());
    if (recipes.length > 0) {
      cachedRecipesPool = recipes;
      lastCacheTime = now;
    }
    return recipes;
  } catch (error) {
    console.error('Erro ao montar catálogo amplo do TheMealDB:', error);
    return cachedRecipesPool || [];
  }
}

/**
 * Busca receitas com filtro de termo e categoria traduzida
 */
export async function searchTheMealDb(query: string = '', categoryPt?: string): Promise<GustoNormalizedRecipe[]> {
  const allRecipes = await getAllTheMealDbRecipes();

  let filtered = allRecipes;

  if (query.trim()) {
    const q = query.trim().toLowerCase();
    filtered = filtered.filter(
      r =>
        r.title.toLowerCase().includes(q) ||
        r.category.toLowerCase().includes(q) ||
        r.ingredients.some(i => i.name.toLowerCase().includes(q))
    );
  }

  if (categoryPt && categoryPt !== 'Todos') {
    const targetCat = categoryPt.toLowerCase();
    const enCat = CATEGORY_REVERSE_MAP[targetCat]?.toLowerCase();
    filtered = filtered.filter(
      r =>
        r.category.toLowerCase() === targetCat ||
        (enCat && r.category.toLowerCase() === enCat) ||
        r.category.toLowerCase().includes(targetCat)
    );
  }

  return filtered;
}

const textTranslationCache = new Map<string, string>();

/**
 * Traduz textos livres (instruções do modo de preparo, títulos) para Português
 */
export async function translateTextToPt(text: string): Promise<string> {
  const trimmed = text.trim();
  if (!trimmed || trimmed.length < 3) return text;

  if (textTranslationCache.has(trimmed)) {
    return textTranslationCache.get(trimmed)!;
  }

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4000);
    const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=pt&dt=t&q=${encodeURIComponent(trimmed)}`;
    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timeout);

    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data) && Array.isArray(data[0])) {
        const translated = data[0].map((s: any) => s[0]).join('');
        if (translated && translated.trim()) {
          textTranslationCache.set(trimmed, translated.trim());
          return translated.trim();
        }
      }
    }
  } catch (err) {
    // Retorna fallback original se falhar ou der timeout
  }

  return text;
}

export async function getTheMealDbRecipeById(mealId: string): Promise<GustoNormalizedRecipe | null> {
  try {
    const cleanId = mealId.replace(/^mealdb-/, '');
    const response = await fetch(`${THE_MEAL_DB_BASE_URL}/lookup.php?i=${encodeURIComponent(cleanId)}`);
    if (!response.ok) return null;

    const data = await response.json();
    if (!data.meals || data.meals.length === 0) return null;

    const baseRecipe = mapMealDbToGusto(data.meals[0]);

    // Traduzir todas as instruções do modo de preparo de forma paralela
    const translatedSteps = await Promise.all(
      baseRecipe.steps.map(async (step) => ({
        orderNumber: step.orderNumber,
        instruction: await translateTextToPt(step.instruction),
      }))
    );

    // Traduzir também o título para português se necessário
    const translatedTitle = await translateTextToPt(baseRecipe.title);

    return {
      ...baseRecipe,
      title: translatedTitle,
      steps: translatedSteps,
    };
  } catch (error) {
    console.error('Erro ao buscar receita por ID do TheMealDB:', error);
    return null;
  }
}

function mapMealDbToGusto(meal: Record<string, any>): GustoNormalizedRecipe {
  const ingredients: GustoNormalizedIngredient[] = [];

  for (let i = 1; i <= 20; i++) {
    const ingredientName = meal[`strIngredient${i}`];
    const measure = meal[`strMeasure${i}`];

    if (ingredientName && ingredientName.trim()) {
      const { quantity, unit } = parseMeasure(measure || '1 unidade');
      ingredients.push({
        name: translateIngredientName(ingredientName),
        quantity,
        unit: translateUnit(unit),
      });
    }
  }

  // Parse instruções em passos numerados
  const rawInstructions: string = meal.strInstructions || '';
  const stepLines = rawInstructions
    .split(/\r?\n+/)
    .map(line => line.trim())
    .filter(line => line.length > 5);

  const steps = stepLines.map((instruction, index) => ({
    orderNumber: index + 1,
    instruction: instruction.replace(/^[0-9]+[.)]\s*/, ''),
  }));

  const rawCat = meal.strCategory || 'Geral';
  const translatedCategory = translateCategory(rawCat);

  return {
    id: `mealdb-${meal.idMeal}`,
    externalId: meal.idMeal,
    title: meal.strMeal || 'Receita Tradicional',
    description: `${translatedCategory} • Culinária ${meal.strArea || 'Internacional'}`,
    prepTimeMin: 35,
    servings: 4,
    category: translatedCategory,
    imageUrl: meal.strMealThumb || null,
    source: 'THE_MEAL_DB',
    author: 'Chef TheMealDB',
    createdAt: new Date().toISOString(),
    ingredients,
    steps: steps.length > 0 ? steps : [{ orderNumber: 1, instruction: rawInstructions }],
  };
}

function parseMeasure(measureStr: string): { quantity: number; unit: string } {
  const trimmed = measureStr.trim();
  const match = trimmed.match(/^([\d/.\s]+)(.*)$/);

  if (match) {
    let numStr = match[1].trim();
    let unit = match[2].trim() || 'unidade';

    let quantity = 1;
    if (numStr.includes('/')) {
      const parts = numStr.split(' ');
      if (parts.length === 2) {
        const frac = parts[1].split('/');
        quantity = parseFloat(parts[0]) + parseFloat(frac[0]) / parseFloat(frac[1]);
      } else {
        const frac = parts[0].split('/');
        quantity = parseFloat(frac[0]) / parseFloat(frac[1]);
      }
    } else {
      const parsed = parseFloat(numStr);
      if (!isNaN(parsed)) quantity = parsed;
    }

    return { quantity: Math.round(quantity * 10) / 10, unit: translateUnit(unit) };
  }

  return { quantity: 1, unit: translateUnit(trimmed || 'unidade') };
}
