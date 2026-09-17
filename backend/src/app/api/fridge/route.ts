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

const addFridgeItemSchema = z.object({
  name: z.string().min(1, 'Nome do ingrediente obrigatório').max(100),
});

const syncFridgeItemsSchema = z.object({
  items: z.array(z.string().min(1).max(100)),
});

// GET /api/fridge - Lista ingredientes da geladeira do usuário
export async function GET(req: NextRequest) {
  try {
    const userId = getUserIdFromRequest(req);
    if (!userId) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const items = await prisma.fridgeItem.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      select: { name: true },
    });

    return NextResponse.json({
      data: items.map(item => item.name),
      message: 'Ingredientes da geladeira recuperados com sucesso',
    });
  } catch (error) {
    console.error('Erro ao buscar geladeira:', error);
    return NextResponse.json({ error: 'Erro interno ao buscar ingredientes' }, { status: 500 });
  }
}

// POST /api/fridge - Adiciona um ingrediente ou sincroniza lista
export async function POST(req: NextRequest) {
  try {
    const userId = getUserIdFromRequest(req);
    if (!userId) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const body = await req.json();

    // Caso seja sincronização em lote
    if (Array.isArray(body?.items)) {
      const parsed = syncFridgeItemsSchema.safeParse(body);
      if (!parsed.success) {
        return NextResponse.json({ error: 'Formato de itens inválido' }, { status: 400 });
      }

      // Deleta itens antigos e recria a lista sincronizada
      await prisma.$transaction(async (tx) => {
        await tx.fridgeItem.deleteMany({ where: { userId } });
        const uniqueNames = Array.from(new Set(parsed.data.items.map(i => i.trim())));
        if (uniqueNames.length > 0) {
          await tx.fridgeItem.createMany({
            data: uniqueNames.map(name => ({
              name,
              userId,
            })),
            skipDuplicates: true,
          });
        }
      });

      const updated = await prisma.fridgeItem.findMany({
        where: { userId },
        orderBy: { createdAt: 'desc' },
        select: { name: true },
      });

      return NextResponse.json({
        data: updated.map(u => u.name),
        message: 'Geladeira sincronizada com sucesso',
      });
    }

    // Caso seja inserção de um único item
    const parsed = addFridgeItemSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json({ error: 'Nome do ingrediente inválido' }, { status: 400 });
    }

    const cleanName = parsed.data.name.trim();

    await prisma.fridgeItem.upsert({
      where: {
        userId_name: {
          userId,
          name: cleanName,
        },
      },
      update: {},
      create: {
        name: cleanName,
        userId,
      },
    });

    const items = await prisma.fridgeItem.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      select: { name: true },
    });

    return NextResponse.json({
      data: items.map(item => item.name),
      message: 'Ingrediente adicionado à geladeira',
    });
  } catch (error) {
    console.error('Erro ao adicionar na geladeira:', error);
    return NextResponse.json({ error: 'Erro interno ao salvar ingrediente' }, { status: 500 });
  }
}

// DELETE /api/fridge - Remove um item ou limpa a geladeira
export async function DELETE(req: NextRequest) {
  try {
    const userId = getUserIdFromRequest(req);
    if (!userId) {
      return NextResponse.json({ error: 'Não autorizado' }, { status: 401 });
    }

    const { searchParams } = new URL(req.url);
    const name = searchParams.get('name');
    const clearAll = searchParams.get('clear') === 'true';

    if (clearAll) {
      await prisma.fridgeItem.deleteMany({ where: { userId } });
      return NextResponse.json({
        data: [],
        message: 'Geladeira esvaziada com sucesso',
      });
    }

    if (!name) {
      return NextResponse.json({ error: 'Informe o ingrediente a remover' }, { status: 400 });
    }

    await prisma.fridgeItem.deleteMany({
      where: {
        userId,
        name: { equals: name.trim(), mode: 'insensitive' },
      },
    });

    const items = await prisma.fridgeItem.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      select: { name: true },
    });

    return NextResponse.json({
      data: items.map(item => item.name),
      message: 'Ingrediente removido com sucesso',
    });
  } catch (error) {
    console.error('Erro ao remover da geladeira:', error);
    return NextResponse.json({ error: 'Erro interno ao remover ingrediente' }, { status: 500 });
  }
}
