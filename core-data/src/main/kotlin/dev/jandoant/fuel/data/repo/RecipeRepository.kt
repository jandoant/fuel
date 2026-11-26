package dev.jandoant.fuel.data.repo

import dev.jandoant.fuel.core.domain.Food
import dev.jandoant.fuel.core.domain.Portion
import dev.jandoant.fuel.core.domain.RecipeTag
import dev.jandoant.fuel.core.domain.RecipeTemplate
import dev.jandoant.fuel.data.db.FuelDatabase
import dev.jandoant.fuel.data.db.IdCount
import dev.jandoant.fuel.data.db.RecipeEntity
import dev.jandoant.fuel.data.db.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Recipe templates (PRD §7.10). Planning copies ingredients into the entry; nothing here touches entries. */
class RecipeRepository(
    private val db: FuelDatabase,
    private val foods: FoodRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.recipeDao()

    val all: Flow<List<RecipeTemplate>> =
        combine(dao.observeAll(), dao.observeAllIngredients(), dao.observeUsage(), foods.catalog) { r, i, u, cat -> assemble(r, i, u, cat) }

    fun observe(id: Long): Flow<RecipeTemplate?> = all.map { list -> list.firstOrNull { it.id == id } }

    suspend fun get(id: Long): RecipeTemplate? {
        val r = dao.byId(id) ?: return null
        val ing = dao.ingredients(id)
        val cat = ing.mapNotNull { foods.get(it.foodId) }.associateBy { it.id }
        return assemble(listOf(r), ing, listOf(IdCount(id, dao.usage(id))), cat).first()
    }

    suspend fun create(name: String, tags: Set<RecipeTag> = emptySet(), ingredients: List<PortionDraft> = emptyList()): Long = db.tx {
        val id = dao.insert(RecipeEntity(name = name.trim().ifBlank { "New recipe" }, tags = tags.joinToString(",") { it.name }, createdAt = now()))
        dao.insertIngredients(ingredients.mapIndexed { i, p -> RecipeIngredientEntity(recipeId = id, foodId = p.foodId, unitId = p.unitId, amount = p.amount, sortOrder = i) })
        id
    }

    suspend fun update(id: Long, name: String? = null, tags: Set<RecipeTag>? = null, favourite: Boolean? = null) {
        val r = dao.byId(id) ?: return
        dao.update(
            r.copy(
                name = name?.trim()?.ifBlank { r.name } ?: r.name,
                tags = tags?.joinToString(",") { it.name } ?: r.tags,
                favourite = favourite ?: r.favourite,
            ),
        )
    }

    suspend fun setIngredients(id: Long, ingredients: List<PortionDraft>) =
        dao.replaceIngredients(id, ingredients.mapIndexed { i, p -> RecipeIngredientEntity(recipeId = id, foodId = p.foodId, unitId = p.unitId, amount = p.amount, sortOrder = i) })

    suspend fun addIngredient(id: Long, p: PortionDraft) {
        val cur = dao.ingredients(id).map { PortionDraft(it.foodId, it.unitId, it.amount) }
        setIngredients(id, cur + p)
        foods.touch(listOf(p.foodId))
    }

    /** Deleting a template keeps planned entries (they own their ingredients; templateId stays as a dead reference). */
    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        fun assemble(recipes: List<RecipeEntity>, ingredients: List<RecipeIngredientEntity>, usage: List<IdCount>, catalog: Map<Long, Food>): List<RecipeTemplate> {
            val byRecipe = ingredients.groupBy { it.recipeId }
            val uses = usage.associate { it.id to it.n }
            return recipes.map { r ->
                RecipeTemplate(
                    id = r.id,
                    name = r.name,
                    tags = r.tags.split(',').mapNotNull { t -> RecipeTag.entries.firstOrNull { it.name == t } }.toSet(),
                    favourite = r.favourite,
                    ingredients = byRecipe[r.id].orEmpty().sortedBy { it.sortOrder }.mapNotNull { i ->
                        val f = catalog[i.foodId] ?: return@mapNotNull null
                        Portion(i.id, f, f.unit(i.unitId), i.amount)
                    },
                    usageCount = uses[r.id] ?: 0,
                )
            }
        }
    }
}
