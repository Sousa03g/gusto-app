package com.gusto.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gusto.app.data.model.CreateRecipeRequest
import com.gusto.app.data.model.CreateStepRequest
import com.gusto.app.data.model.Ingredient
import com.gusto.app.data.repository.RecipeRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRecipeScreen(
    recipeRepository: RecipeRepository,
    onBack: () -> Unit,
    onRecipeCreated: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var prepTimeMin by remember { mutableStateOf("30") }
    var servings by remember { mutableStateOf("2") }
    var category by remember { mutableStateOf("Massa") }
    var imageUrl by remember { mutableStateOf("") }

    val ingredients = remember {
        mutableStateListOf(
            Ingredient(name = "Azeite extravirgem", quantity = 2.0, unit = "colheres de sopa"),
            Ingredient(name = "Alho picado", quantity = 3.0, unit = "dentes")
        )
    }

    val steps = remember {
        mutableStateListOf(
            "Aqueça o azeite em fogo médio em uma frigideira funda.",
            "Adicione o alho picado e doure levemente sem queimar."
        )
    }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova Receita", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (errorMessage != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título da Receita *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Breve Descrição") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = prepTimeMin,
                        onValueChange = { prepTimeMin = it },
                        label = { Text("Tempo (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = servings,
                        onValueChange = { servings = it },
                        label = { Text("Porções") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoria (ex: Massa, Sobremesa)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("URL da Imagem (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Dynamic Ingredients
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ingredientes", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        ingredients.add(Ingredient(name = "", quantity = 1.0, unit = "unidade"))
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar")
                    }
                }
            }

            itemsIndexed(ingredients) { index, ingredient ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ingredient.name,
                        onValueChange = { newName ->
                            ingredients[index] = ingredient.copy(name = newName)
                        },
                        label = { Text("Ingrediente") },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = ingredient.quantity.toString(),
                        onValueChange = { newQty ->
                            val parsed = newQty.toDoubleOrNull() ?: 1.0
                            ingredients[index] = ingredient.copy(quantity = parsed)
                        },
                        label = { Text("Qtd") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = ingredient.unit,
                        onValueChange = { newUnit ->
                            ingredients[index] = ingredient.copy(unit = newUnit)
                        },
                        label = { Text("Un.") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    IconButton(onClick = { if (ingredients.size > 1) ingredients.removeAt(index) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remover",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Dynamic Steps
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modo de Preparo", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        steps.add("")
                    }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar Passo")
                    }
                }
            }

            itemsIndexed(steps) { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = step,
                        onValueChange = { steps[index] = it },
                        label = { Text("Passo ${index + 1}") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2
                    )
                    IconButton(onClick = { if (steps.size > 1) steps.removeAt(index) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remover",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Submit Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            errorMessage = "O título é obrigatório"
                            return@Button
                        }
                        coroutineScope.launch {
                            isSaving = true
                            errorMessage = null
                            val request = CreateRecipeRequest(
                                title = title,
                                description = description.ifBlank { null },
                                prepTimeMin = prepTimeMin.toIntOrNull() ?: 30,
                                servings = servings.toIntOrNull() ?: 2,
                                category = category.ifBlank { "Geral" },
                                imageUrl = imageUrl.ifBlank { null },
                                ingredients = ingredients.filter { it.name.isNotBlank() },
                                steps = steps.filter { it.isNotBlank() }.mapIndexed { idx, st ->
                                    CreateStepRequest(orderNumber = idx + 1, instruction = st)
                                }
                            )

                            val res = recipeRepository.createRecipe(request)
                            res.onSuccess {
                                onRecipeCreated()
                            }.onFailure {
                                errorMessage = it.message ?: "Erro ao salvar receita"
                            }
                            isSaving = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Publicar Receita", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
