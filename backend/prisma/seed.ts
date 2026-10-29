import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcryptjs';

const prisma = new PrismaClient();

async function main() {
  console.log('Seeding initial data...');

  const passwordHash = await bcrypt.hash('Password123', 12);

  const user = await prisma.user.upsert({
    where: { email: 'chef@gusto.com' },
    update: {},
    create: {
      email: 'chef@gusto.com',
      name: 'Chef Auguste',
      passwordHash,
      themePreference: 'OLED_BLACK',
    },
  });

  console.log(`User created/found: ${user.email} (${user.id})`);

  // Check if initial recipe exists
  const existingRecipe = await prisma.recipe.findFirst({
    where: { userId: user.id, title: 'Fettuccine ao Pesto Fresco' },
  });

  if (!existingRecipe) {
    await prisma.recipe.create({
      data: {
        title: 'Fettuccine ao Pesto Fresco',
        description: 'Massa artesanal italiana envolta em pesto de manjericão, pinoli tostados e queijo Parmigiano-Reggiano.',
        prepTimeMin: 20,
        servings: 2,
        category: 'Massa',
        imageUrl: 'https://images.unsplash.com/photo-1551183053-bf91a1d81141?w=800&auto=format&fit=crop',
        userId: user.id,
        source: 'GUSTO_USER',
        ingredients: {
          create: [
            { name: 'Fettuccine de grano duro', quantity: 250, unit: 'g' },
            { name: 'Folhas frescas de manjericão', quantity: 50, unit: 'g' },
            { name: 'Azeite de oliva extravirgem', quantity: 60, unit: 'ml' },
            { name: 'Queijo Parmigiano-Reggiano ralado', quantity: 40, unit: 'g' },
            { name: 'Dente de alho', quantity: 1, unit: 'unidade' },
            { name: 'Nozes ou Pinoli tostados', quantity: 30, unit: 'g' },
            { name: 'Sal marinho e pimenta-do-reino', quantity: 1, unit: 'a gosto' },
          ],
        },
        steps: {
          create: [
            { orderNumber: 1, instruction: 'Ferva 2,5 litros de água com sal abundante em uma panela grande.' },
            { orderNumber: 2, instruction: 'No pilão ou processador, triture o alho, os pinoli e o sal até formar uma pasta.' },
            { orderNumber: 3, instruction: 'Adicione as folhas de manjericão e processe aos pulsos com o azeite em fio contínuo.' },
            { orderNumber: 4, instruction: 'Cozinhe o fettuccine al dente (cerca de 8 a 9 minutos) e reserve 1 concha da água do cozimento.' },
            { orderNumber: 5, instruction: 'Em uma tigela morna, envolva a massa no pesto, adicione a água reservada e o parmesão até emulsionar. Sirva imediatamente.' },
          ],
        },
      },
    });
    console.log('Sample recipe created!');
  } else {
    console.log('Sample recipe already exists.');
  }
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
