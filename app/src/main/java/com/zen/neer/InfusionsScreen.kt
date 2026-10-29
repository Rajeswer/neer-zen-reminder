package com.zen.neer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal enum class InfusionTime(val label: String) {
    Morning("Morning"),
    Afternoon("Afternoon"),
    AfterMeals("After meals")
}

internal data class Infusion(
    val name: String,
    val tamil: String,
    val latin: String,
    val note: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val bestTime: String,
    val times: Set<InfusionTime>,
    /** Steep timer length in minutes, or null for an overnight soak. */
    val steepMinutes: Int?,
    val method: String,
    val herb: Herb
)

/** Traditional home preparations. Descriptions stay factual: no health claims (design system §9.3). */
internal val infusions = listOf(
    Infusion(
        name = "Jeera Water",
        tamil = "சீரகத் தண்ணீர் · Seeraga neer",
        latin = "Cuminum cyminum",
        note = "A simple, earthy everyday infusion.",
        ingredients = listOf("1 tsp cumin seeds", "500 ml water"),
        steps = listOf(
            "Add the cumin seeds to the water.",
            "Simmer gently for 5 minutes.",
            "Cover and let it rest for 5 minutes.",
            "Strain and sip warm."
        ),
        bestTime = "Morning or after a meal",
        times = setOf(InfusionTime.Morning, InfusionTime.AfterMeals),
        steepMinutes = 5,
        method = "Simmer, then rest",
        herb = Herb.Cumin
    ),
    Infusion(
        name = "Vendhaya Water",
        tamil = "வெந்தயத் தண்ணீர் · Vendhaya neer",
        latin = "Trigonella foenum-graecum",
        note = "A traditional fenugreek soak.",
        ingredients = listOf("1 tsp fenugreek seeds", "1 glass water"),
        steps = listOf(
            "Rinse the seeds.",
            "Soak them overnight in the water, in a clean covered vessel.",
            "Strain in the morning before drinking."
        ),
        bestTime = "Morning",
        times = setOf(InfusionTime.Morning),
        steepMinutes = null,
        method = "Overnight soak",
        herb = Herb.Fenugreek
    ),
    Infusion(
        name = "Omam Water",
        tamil = "ஓமத் தண்ணீர் · Oma neer",
        latin = "Trachyspermum ammi",
        note = "Warm, aromatic ajwain water.",
        ingredients = listOf("½ tsp ajwain", "400 ml water"),
        steps = listOf(
            "Dry-roast the ajwain lightly for a few seconds.",
            "Add the water.",
            "Simmer for 4 minutes.",
            "Strain and let it cool slightly."
        ),
        bestTime = "After a heavy meal",
        times = setOf(InfusionTime.AfterMeals),
        steepMinutes = 4,
        method = "Roast, then simmer",
        herb = Herb.Ajwain
    ),
    Infusion(
        name = "Sombu Water",
        tamil = "சோம்புத் தண்ணீர் · Sombu neer",
        latin = "Foeniculum vulgare",
        note = "A naturally aromatic fennel infusion.",
        ingredients = listOf("1 tsp fennel seeds", "500 ml hot water"),
        steps = listOf(
            "Place the fennel seeds in a cup or jug.",
            "Pour over the hot water.",
            "Cover and steep for 7 minutes.",
            "Strain."
        ),
        bestTime = "Late afternoon",
        times = setOf(InfusionTime.Afternoon),
        steepMinutes = 7,
        method = "Steep",
        herb = Herb.Fennel
    ),
    Infusion(
        name = "Vettiver Water",
        tamil = "வெட்டிவேர் · Vettiver neer",
        latin = "Chrysopogon zizanioides",
        note = "A fragrant, traditional water infusion.",
        ingredients = listOf("A small bundle of food-safe vetiver roots", "1 litre water"),
        steps = listOf(
            "Wash the roots thoroughly.",
            "Place them in the water in a clean, covered vessel.",
            "Infuse for 2 hours.",
            "Remove and discard the roots."
        ),
        bestTime = "Warm afternoons",
        times = setOf(InfusionTime.Afternoon),
        steepMinutes = 120,
        method = "Cold infusion",
        herb = Herb.Vetiver
    )
)

internal fun steepLabel(minutes: Int): String =
    if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}-hour" else "$minutes-min"

@Composable
internal fun InfusionsScreen(onStartSteep: (Int) -> Unit, onOpenSettings: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(-1) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = selected >= 0) { selected = -1 }

    AnimatedContent(
        targetState = selected,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(160)) },
        label = "infusion-nav"
    ) { index ->
        val inf = infusions.getOrNull(index)
        if (inf == null) {
            InfusionList(
                filter = filter?.let { name -> InfusionTime.values().firstOrNull { it.name == name } },
                onFilter = { filter = it?.name },
                onOpen = { selected = it },
                onOpenSettings = onOpenSettings
            )
        } else {
            InfusionDetail(inf, onBack = { selected = -1 }, onStartSteep = { onStartSteep(index) })
        }
    }
}

@Composable
private fun InfusionList(
    filter: InfusionTime?,
    onFilter: (InfusionTime?) -> Unit,
    onOpen: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val visible = infusions.withIndex().filter { filter == null || filter in it.value.times }
    Column(Modifier.fillMaxSize()) {
        ZenTopBar(
            title = "Herbal Infusions",
            actionIcon = Icons.Outlined.Settings,
            actionLabel = "Settings",
            onAction = onOpenSettings
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(horizontal = Space.screenInset, vertical = Space.s8),
            horizontalArrangement = Arrangement.spacedBy(Space.s8)
        ) {
            ZenChip(label = "All", selected = filter == null, onClick = { onFilter(null) })
            InfusionTime.values().forEach { t ->
                ZenChip(label = t.label, selected = filter == t, onClick = { onFilter(t) })
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.screenInset, end = Space.screenInset, top = Space.s8, bottom = Space.s24),
            verticalArrangement = Arrangement.spacedBy(Space.s12)
        ) {
            if (visible.isEmpty()) {
                item { ZenEmptyState("No infusions for this time", "Choose another time of day, or All.") }
            }
            items(visible, key = { it.value.name }) { (index, inf) ->
                InfusionCard(inf, onClick = { onOpen(index) })
            }
            item {
                Text(
                    "Traditional home preparations, not medical advice. Ask a doctor first if you are pregnant, take medication or have a health condition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zen.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s8, start = Space.s8, end = Space.s8)
                )
            }
        }
    }
}

@Composable
private fun InfusionCard(inf: Infusion, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .clickable(onClickLabel = "Open recipe", role = Role.Button, onClick = onClick)
            .then(Modifier)
            .heightIn(min = 96.dp)
            .padding(Space.s12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(Radius.control)
                .then(Modifier)
        ) {
            IllustrationInfusionGlass(inf.herb, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(Space.s16))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(inf.name, style = MaterialTheme.typography.headlineSmall, color = Zen.textPrimary)
            Text(inf.note, style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary)
            Text(inf.bestTime, style = MaterialTheme.typography.labelMedium, color = Palette.DeepSage)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Zen.iconSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
    ZenDivider(Modifier.padding(start = 100.dp))
}

@Composable
private fun InfusionDetail(inf: Infusion, onBack: () -> Unit, onStartSteep: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ZenTopBar(
            title = "",
            navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            navigationLabel = "Back to infusions",
            onNavigate = onBack
        )
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            IllustrationBambooEdge(Modifier.align(Alignment.TopEnd).size(110.dp, 140.dp), mirrored = true, seed = 21L)
            IllustrationInfusionGlass(inf.herb, Modifier.align(Alignment.Center).size(210.dp))
        }
        Column(Modifier.padding(horizontal = Space.screenInset)) {
            Text(
                inf.name,
                style = MaterialTheme.typography.headlineLarge,
                color = Zen.textPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(inf.tamil, style = MaterialTheme.typography.bodyMedium, color = Zen.textSecondary)
            Text(inf.latin, style = ZenQuote.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize), color = Zen.textSecondary)
            Spacer(Modifier.height(Space.s20))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                Fact(Icons.Outlined.WbSunny, inf.bestTime, "Best time", Modifier.weight(1f))
                Fact(
                    Icons.Outlined.Timer,
                    inf.steepMinutes?.let { steepLabel(it).replace("-", " ") } ?: "Overnight",
                    "Steep time",
                    Modifier.weight(1f)
                )
                Fact(Icons.Outlined.Eco, inf.method, "Method", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.s24))
            ZenSurface(Modifier.fillMaxWidth()) {
                ZenSectionHeader("Ingredients", editorial = true)
                Spacer(Modifier.height(Space.s8))
                inf.ingredients.forEach { item ->
                    Text("·  $item", style = MaterialTheme.typography.bodyLarge, color = Zen.textPrimary, modifier = Modifier.padding(vertical = Space.s2))
                }
            }
            Spacer(Modifier.height(Space.s24))
            ZenSectionHeader("Preparation", editorial = true)
            Spacer(Modifier.height(Space.s12))
            inf.steps.forEachIndexed { i, step ->
                Row(Modifier.fillMaxWidth().padding(bottom = Space.s12)) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.DeepSage,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(step, style = MaterialTheme.typography.bodyLarge, color = Zen.textPrimary, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(Space.s16))
            val minutes = inf.steepMinutes
            if (minutes != null) {
                ZenPrimaryButton(
                    text = "Start ${steepLabel(minutes)} steep timer",
                    icon = Icons.Outlined.Timer,
                    onClick = onStartSteep,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ZenSurface(Modifier.fillMaxWidth(), color = Zen.surfaceSecondary) {
                    Text(
                        "No timer needed. Prepare it the evening before and strain it in the morning.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Zen.textPrimary
                    )
                }
            }
            Spacer(Modifier.height(Space.s16))
            Text(
                "Traditional home preparation, not medical advice.",
                style = MaterialTheme.typography.bodySmall,
                color = Zen.textSecondary,
                modifier = Modifier.padding(bottom = Space.s24)
            )
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s4)
    ) {
        Icon(icon, contentDescription = null, tint = Zen.iconSecondary, modifier = Modifier.size(22.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = Zen.textPrimary, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Zen.textSecondary, textAlign = TextAlign.Center)
    }
}
