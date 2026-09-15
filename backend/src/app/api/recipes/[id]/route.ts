import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { prisma } from '@/lib/prisma';
import { verifyAccessToken } from '@/lib/auth';
import { getTheMealDbRecipeById } from '@/lib/mealdb';

const updateRecipeSchema = z.object({
  title: z.string().min(3).max(150).optional(),
  description: z.string().optional(),
  prepTimeMin: z.number().int().min(1).optional(),
  servings: z.number().int().min(1).optional(),
  category: z.string().min(2).optional(),
  imageUrl: z.string().url().optional().or(z.literal('')),
  ingredients: z.array(z.object({
    name: z.string(),
    quantity: z.number(),
    unit: z.string(),
  })).optional(),
  steps: z.array(z.object({
    orderNumber: z.number(),
    instruction: z.string(),
  })).optional(),
});

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;

    // Check if it's an external MealDB recipe
    if (id.startsWith('mealdb-')) {
      const externalId = id.replace('mealdb-', '');
      const recipe = await getTheMealDbRecipeById(externalId);
      if (!recipe) {
        return NextResponse.json({ error: 'Receita não encontrada na API externa' }, { status: 404 });
      }
      return NextResponse.json(recipe);
    }

    // Database recipe
    const recipe = await prisma.recipe.findUnique({
      where: { id },
      include: {
        ingredients: true,
        steps: { orderBy: { orderNumber: 'asc' } },
        user: { select: { id: true, name: true } },
      },
    });

    if (!recipe) {
      return NextResponse.json({ error: 'Receita não encontrada' }, { status: 404 });
    }

    return NextResponse.json({
      id: recipe.id,
      title: recipe.title,
      description: recipe.description,
      prepTimeMin: recipe.prepTimeMin,
      servings: recipe.servings,
      category: recipe.category,
      imageUrl: recipe.imageUrl,
      source: recipe.source,
      author: recipe.user.name,
      userId: recipe.userId,
      createdAt: recipe.createdAt.toISOString(),
      ingredients: recipe.ingredients.map(i => ({ name: i.name, quantity: i.quantity, unit: i.unit })),
      steps: recipe.steps.map(s => ({ orderNumber: s.orderNumber, instruction: s.instruction })),
    });
  } catch (error) {
    console.error('Fetch recipe by id error:', error);
    return NextResponse.json({ error: 'Erro ao buscar receita' }, { status: 500 });
  }
}

export async function PUT(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const authHeader = req.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const token = authHeader.split(' ')[1];
    const userPayload = verifyAccessToken(token);
    if (!userPayload) {
      return NextResponse.json({ error: 'Token inválido' }, { status: 401 });
    }

    const existing = await prisma.recipe.findUnique({ where: { id } });
    if (!existing) {
      return NextResponse.json({ error: 'Receita não encontrada' }, { status: 404 });
    }
    if (existing.userId !== userPayload.userId) {
      return NextResponse.json({ error: 'Você não tem permissão para alterar esta receita' }, { status: 403 });
    }

    const body = await req.json();
    const validated = updateRecipeSchema.safeParse(body);
    if (!validated.success) {
      return NextResponse.json({ error: 'Dados inválidos', details: validated.error.flatten() }, { status: 400 });
    }

    const { title, description, prepTimeMin, servings, category, imageUrl, ingredients, steps } = validated.data;

    // Use transaction to update fields and replace ingredients/steps if provided
    const updated = await prisma.$transaction(async (tx) => {
      if (ingredients) {
        await tx.recipeIngredient.deleteMany({ where: { recipeId: id } });
        await tx.recipeIngredient.createMany({
          data: ingredients.map(ing => ({
            recipeId: id,
            name: ing.name,
            quantity: ing.quantity,
            unit: ing.unit,
          })),
        });
      }

      if (steps) {
        await tx.recipeStep.deleteMany({ where: { recipeId: id } });
        await tx.recipeStep.createMany({
          data: steps.map((st, idx) => ({
            recipeId: id,
            orderNumber: st.orderNumber || idx + 1,
            instruction: st.instruction,
          })),
        });
      }

      return tx.recipe.update({
        where: { id },
        data: {
          ...(title && { title }),
          ...(description !== undefined && { description }),
          ...(prepTimeMin && { prepTimeMin }),
          ...(servings && { servings }),
          ...(category && { category }),
          ...(imageUrl !== undefined && { imageUrl: imageUrl || null }),
        },
        include: {
          ingredients: true,
          steps: { orderBy: { orderNumber: 'asc' } },
        },
      });
    });

    return NextResponse.json(updated);
  } catch (error) {
    console.error('Update recipe error:', error);
    return NextResponse.json({ error: 'Erro ao atualizar receita' }, { status: 500 });
  }
}

export async function DELETE(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const authHeader = req.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const token = authHeader.split(' ')[1];
    const userPayload = verifyAccessToken(token);
    if (!userPayload) {
      return NextResponse.json({ error: 'Token inválido' }, { status: 401 });
    }

    const existing = await prisma.recipe.findUnique({ where: { id } });
    if (!existing) {
      return NextResponse.json({ error: 'Receita não encontrada' }, { status: 404 });
    }
    if (existing.userId !== userPayload.userId) {
      return NextResponse.json({ error: 'Você não tem permissão para deletar esta receita' }, { status: 403 });
    }

    await prisma.recipe.delete({ where: { id } });

    return NextResponse.json({ message: 'Receita removida com sucesso' });
  } catch (error) {
    console.error('Delete recipe error:', error);
    return NextResponse.json({ error: 'Erro ao deletar receita' }, { status: 500 });
  }
}
