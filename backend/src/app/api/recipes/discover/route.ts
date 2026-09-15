import { NextRequest, NextResponse } from 'next/server';
import { searchTheMealDb } from '@/lib/mealdb';

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const query = searchParams.get('q') || '';
    const category = searchParams.get('category') || '';

    let recipes = await searchTheMealDb(query);

    if (category && category !== 'Todos') {
      recipes = recipes.filter(r => r.category.toLowerCase().includes(category.toLowerCase()));
    }

    return NextResponse.json({
      data: recipes,
      source: 'TheMealDB Open API',
      count: recipes.length,
    });
  } catch (error) {
    console.error('Discover error:', error);
    return NextResponse.json({ error: 'Erro ao descobrir receitas externas' }, { status: 500 });
  }
}
