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

export async function searchTheMealDb(query: string = ''): Promise<GustoNormalizedRecipe[]> {
  try {
    const endpoint = query.trim()
      ? `${THE_MEAL_DB_BASE_URL}/search.php?s=${encodeURIComponent(query)}`
      : `${THE_MEAL_DB_BASE_URL}/search.php?s=chicken`; // Default discovery search

    const response = await fetch(endpoint);
    if (!response.ok) return [];

    const data = await response.json();
    if (!data.meals || !Array.isArray(data.meals)) return [];

    return data.meals.map(mapMealDbToGusto);
  } catch (error) {
    console.error('Error fetching from TheMealDB:', error);
    return [];
  }
}

export async function getTheMealDbRecipeById(mealId: string): Promise<GustoNormalizedRecipe | null> {
  try {
    const response = await fetch(`${THE_MEAL_DB_BASE_URL}/lookup.php?i=${encodeURIComponent(mealId)}`);
    if (!response.ok) return null;

    const data = await response.json();
    if (!data.meals || data.meals.length === 0) return null;

    return mapMealDbToGusto(data.meals[0]);
  } catch (error) {
    console.error('Error fetching recipe by id from TheMealDB:', error);
    return null;
  }
}

function mapMealDbToGusto(meal: Record<string, any>): GustoNormalizedRecipe {
  const ingredients: GustoNormalizedIngredient[] = [];

  for (let i = 1; i <= 20; i++) {
    const ingredientName = meal[`strIngredient${i}`];
    const measure = meal[`strMeasure${i}`];

    if (ingredientName && ingredientName.trim()) {
      const { quantity, unit } = parseMeasure(measure || '1 unit');
      ingredients.push({
        name: ingredientName.trim(),
        quantity,
        unit,
      });
    }
  }

  // Parse instructions into distinct numbered steps
  const rawInstructions: string = meal.strInstructions || '';
  const stepLines = rawInstructions
    .split(/\r?\n+/)
    .map(line => line.trim())
    .filter(line => line.length > 5);

  const steps = stepLines.map((instruction, index) => ({
    orderNumber: index + 1,
    instruction: instruction.replace(/^[0-9]+[.)]\s*/, ''),
  }));

  return {
    id: `mealdb-${meal.idMeal}`,
    externalId: meal.idMeal,
    title: meal.strMeal || 'Receita Desconhecida',
    description: meal.strCategory ? `${meal.strCategory} (${meal.strArea || 'Internacional'})` : null,
    prepTimeMin: 35, // Estimated standard time
    servings: 4,
    category: meal.strCategory || 'Geral',
    imageUrl: meal.strMealThumb || null,
    source: 'THE_MEAL_DB',
    author: 'TheMealDB',
    createdAt: new Date().toISOString(),
    ingredients,
    steps,
  };
}

function parseMeasure(measureStr: string): { quantity: number; unit: string } {
  const trimmed = measureStr.trim();
  const match = trimmed.match(/^([\d/.\s]+)(.*)$/);

  if (match) {
    let numStr = match[1].trim();
    let unit = match[2].trim() || 'unidade';

    // Parse fractions like 1/2 or 1 1/2
    let quantity = 1;
    if (numStr.includes('/')) {
      const parts = numStr.split(' ');
      if (parts.length === 2) {
        const frac = parts[1].split('/');
        quantity = parseFloat(parts[0]) + (parseFloat(frac[0]) / parseFloat(frac[1]));
      } else {
        const frac = parts[0].split('/');
        quantity = parseFloat(frac[0]) / parseFloat(frac[1]);
      }
    } else {
      const parsed = parseFloat(numStr);
      if (!isNaN(parsed)) quantity = parsed;
    }

    return { quantity: Math.round(quantity * 10) / 10, unit };
  }

  return { quantity: 1, unit: trimmed || 'unidade' };
}
