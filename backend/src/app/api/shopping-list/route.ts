import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { prisma } from '@/lib/prisma';
import { verifyAccessToken } from '@/lib/auth';

function getUserIdFromRequest(req: NextRequest): string | null {
  const authHeader = req.headers.get('authorization');
  if (!authHeader) return null;
  const token = authHeader.replace(/^Bearer\s+/i, '').trim();
  if (!token) return null;
  const payload = verifyAccessToken(token);
  return payload ? payload.userId : null;
}

const addShoppingItemSchema = z.object({
  name: z.string().min(1, 'Nome do item obrigatório').max(100),
  quantity: z.number().optional().nullable(),
  unit: z.string().optional().nullable(),
  sourceRecipeTitle: z.string().optional().nullable(),
});

const batchAddSchema = z.object({
  items: z.array(addShoppingItemSchema),
});

// GET /api/shopping-list - Retorna a lista de compras do usuário
export async function GET(req: NextRequest) {
  try {
    const userId = getUserIdFromRequest(req);
    if (!userId) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const items = await prisma.shoppingListItem.findMany({
      where: { userId },
      orderBy: [{ isChecked: 'asc' }, { createdAt: 'desc' }],
    });

    return NextResponse.json({
      data: items,
      message: 'Lista de compras recuperada com sucesso',
    });
  } catch (error) {
    console.error('Erro ao buscar lista de compras:', error);
    return NextResponse.json({ error: 'Erro interno ao buscar lista de compras' }, { status: 500 });
  }
}

// POST /api/shopping-list - Adiciona um ou múltiplos itens
export async function POST(req: NextRequest) {
  try {
    const userId = getUserIdFromRequest(req);
    if (!userId) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const body = await req.json();

    // Caso seja inserção em lote (ex: ingredientes de uma receita)
    if (Array.isArray(body?.items)) {
      const parsed = batchAddSchema.safeParse(body);
      if (!parsed.success) {
        return NextResponse.json({ error: 'Formato de itens inválido' }, { status: 400 });
      }

      await prisma.shoppingListItem.createMany({
        data: parsed.data.items.map(item => ({
          name: item.name.trim(),
          quantity: item.quantity,
          unit: item.unit,
          sourceRecipeTitle: item.sourceRecipeTitle,
          userId,
        })),
      });

      const updated = await prisma.shoppingListItem.findMany({
        where: { userId },
        orderBy: [{ isChecked: 'asc' }, { createdAt: 'desc' }],
      });

      return NextResponse.json({
        data: updated,
        message: 'Itens adicionados à lista de compras',
      });
    }

    // Inserção individual
    const parsed = addShoppingItemSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json({ error: 'Nome do item inválido' }, { status: 400 });
    }

    const created = await prisma.shoppingListItem.create({
      data: {
        name: parsed.data.name.trim(),
        quantity: parsed.data.quantity,
        unit: parsed.data.unit,
        sourceRecipeTitle: parsed.data.sourceRecipeTitle,
        userId,
      },
    });

    return NextResponse.json({
      data: created,
      message: 'Item adicionado à lista de compras',
    });
  } catch (error) {
    console.error('Erro ao adicionar na lista de compras:', error);
    return NextResponse.json({ error: 'Erro interno ao salvar item' }, { status: 500 });
  }
}

// PATCH /api/shopping-list - Alterna status de comprado
export async function PATCH(req: NextRequest) {
  try {
    const userId = getUserIdFromRequest(req);
    if (!userId) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const body = await req.json();
    const { id, isChecked } = body;

    if (!id || typeof isChecked !== 'boolean') {
      return NextResponse.json({ error: 'Parâmetros inválidos' }, { status: 400 });
    }

    const updated = await prisma.shoppingListItem.updateMany({
      where: { id, userId },
      data: { isChecked },
    });

    return NextResponse.json({
      data: updated,
      message: 'Item atualizado com sucesso',
    });
  } catch (error) {
    console.error('Erro ao atualizar item da lista:', error);
    return NextResponse.json({ error: 'Erro interno ao atualizar item' }, { status: 500 });
  }
}

// DELETE /api/shopping-list - Remove item ou limpa concluídos
export async function DELETE(req: NextRequest) {
  try {
    const userId = getUserIdFromRequest(req);
    if (!userId) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const { searchParams } = new URL(req.url);
    const id = searchParams.get('id');
    const clearCompleted = searchParams.get('clearCompleted') === 'true';
    const clearAll = searchParams.get('clearAll') === 'true';

    if (clearAll) {
      await prisma.shoppingListItem.deleteMany({ where: { userId } });
      return NextResponse.json({ data: [], message: 'Lista de compras esvaziada' });
    }

    if (clearCompleted) {
      await prisma.shoppingListItem.deleteMany({ where: { userId, isChecked: true } });
      return NextResponse.json({ data: [], message: 'Itens comprados removidos' });
    }

    if (!id) {
      return NextResponse.json({ error: 'Informe o ID do item' }, { status: 400 });
    }

    await prisma.shoppingListItem.deleteMany({
      where: { id, userId },
    });

    return NextResponse.json({ message: 'Item removido com sucesso' });
  } catch (error) {
    console.error('Erro ao remover da lista de compras:', error);
    return NextResponse.json({ error: 'Erro interno ao remover item' }, { status: 500 });
  }
}
