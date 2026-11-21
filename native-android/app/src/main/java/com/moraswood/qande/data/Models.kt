package com.moraswood.qande.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * Kotlin mirror of the V10.1 app.js state (see V10.1-inventory.md §3).
 * Field names are identical to the JavaScript objects so that V10.1 JSON backups
 * (format "moras-qe-backup", version 1) decode and re-encode losslessly.
 *
 * Property defaults represent a MISSING field (JS `undefined` -> ""), exactly as app.js sees it.
 * The JS factory functions (blankRow, blankItem, blankCosting, blankQuote, defaultState)
 * are reproduced as the companion `blank()` functions below.
 */

/** Accepts a JSON string, number, boolean or null and keeps it as the text app.js would see. */
object LooseStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LooseString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = input.decodeJsonElement()) {
            is JsonNull -> ""
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }
}

/** Number-or-string -> Int, like JS `Number(v)||0`. */
object LooseIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LooseInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        val input = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = input.decodeJsonElement() as? JsonPrimitive ?: return 0
        return element.doubleOrNull?.toInt() ?: element.content.toDoubleOrNull()?.toInt() ?: 0
    }
}

/** Number-or-string -> Double, like JS `Number(v)||0`. */
object LooseDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LooseDouble", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
    override fun deserialize(decoder: Decoder): Double {
        val input = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val element = input.decodeJsonElement() as? JsonPrimitive ?: return 0.0
        val v = element.doubleOrNull ?: element.content.toDoubleOrNull() ?: 0.0
        return if (v.isNaN()) 0.0 else v
    }
}

/** true only when the JSON value is literally `true` (JS `x===true`); missing/other -> null. */
object LooseBoolSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LooseBool", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
    override fun deserialize(decoder: Decoder): Boolean? {
        val input = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return (input.decodeJsonElement() as? JsonPrimitive)?.booleanOrNull
    }
}

typealias LStr = @Serializable(with = LooseStringSerializer::class) String
typealias LInt = @Serializable(with = LooseIntSerializer::class) Int
typealias LDouble = @Serializable(with = LooseDoubleSerializer::class) Double

val QeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

/** Calculator/estimation measurement row: `{l,lUnit,w,wUnit,t,tUnit,q,mode}`. */
@Serializable
data class Row(
    val l: LStr = "", val lUnit: LStr = "", val w: LStr = "", val wUnit: LStr = "",
    val t: LStr = "", val tUnit: LStr = "", val q: LStr = "", val mode: LStr = "",
    /** Feature G (native): "LT" = vertical piece, its area is length × thickness (default length × width). */
    val areaFace: String? = null,
) {
    companion object { fun blank() = Row("", "ft", "", "in", "", "in", "", "CFT") }
}

/**
 * `{name, rows:[Row]}` used for both `items` (calculator) and `estimationItems`.
 * Native additions (ignored by V10.1, which keeps unknown fields): [tag] = optional furniture-type label
 * for the template library, [photo] = file name of the item photo under filesDir/item_photos/.
 */
@Serializable
data class CalcItem(
    val name: LStr = "",
    val rows: List<Row> = emptyList(),
    val tag: LStr = "",
    val photo: LStr = "",
) {
    companion object { fun blank() = CalcItem("", listOf(Row.blank())) }
}

/** Estimation rate group: `{id,name,mode,color,rate,rows:[index]}`. */
@Serializable
data class RateGroup(
    val id: LStr = "", val name: LStr = "", val mode: LStr = "", val color: LStr = "",
    val rate: LStr = "", val rows: List<LInt> = emptyList(),
)

/** Per-estimation-item costing (`blankCosting`). */
@Serializable
data class Costing(
    val rateGroups: List<RateGroup> = emptyList(),
    val rowRates: List<LStr> = emptyList(),
    val cftRate: LStr = "", val kolRate: LStr = "",
    val labourMode: LStr = "", val labourLength: LStr = "", val labourLengthUnit: LStr = "",
    val labourWidth: LStr = "", val labourWidthUnit: LStr = "", val labourQty: LStr = "",
    val labourRate: LStr = "", val labourLumpsum: LStr = "",
    val polishMode: LStr = "", val polishRate: LStr = "", val polishLumpsum: LStr = "",
    val polishAreaOverride: LStr = "",
    val consumables: LStr = "", val other: LStr = "", val sellingPrice: LStr = "",
    @Serializable(with = LooseBoolSerializer::class) val sellingAuto: Boolean? = null,
    /** Feature G (native): item area override in sq.ft (blank = computed from the rows). */
    val areaOverride: String? = null,
) {
    companion object {
        fun blank() = Costing(
            labourMode = "sqft", labourLengthUnit = "ft", labourWidthUnit = "ft",
            polishMode = "sqft", sellingAuto = true,
        )
    }
}

/** Quotation line: `{description,unit,qty,unitPrice,images:[dataURL],aiOriginal}`. */
@Serializable
data class QuoteItem(
    val description: LStr = "", val unit: LStr = "", val qty: LStr = "", val unitPrice: LStr = "",
    val images: List<LStr> = emptyList(), val aiOriginal: LStr = "",
    /** Feature D (native): optional wood species of this line; omitted from JSON when unset. */
    val species: String? = null,
    /** Feature D (native): CFT/KOL link captured at Send to Quotation, used to re-price the line for another species. */
    val wood: WoodLink? = null,
    /** Feature G (native): item area (sq.ft, 2 decimals) carried from Estimation; printed when "Show area" is on. */
    val area: String? = null,
) {
    companion object { fun blank() = QuoteItem() }
}

/**
 * Feature D: the estimation timber behind a quotation line, captured at Send to Quotation.
 * cft / kol = volumes of the item's CFT rows / KOL rows; woodCft / woodKol = their wood cost at capture (or after the last
 * species re-price); cftRate / kolRate = the base Rate/CFT and Rate/KOL in force for that cost.
 */
@Serializable
data class WoodLink(
    val cft: LDouble = 0.0, val kol: LDouble = 0.0,
    val woodCft: LDouble = 0.0, val woodKol: LDouble = 0.0,
    val cftRate: LStr = "", val kolRate: LStr = "",
)

/** Quotation option: `{name,items,discountType,discountValue,gstPercent}`. */
@Serializable
data class QuoteOption(
    val name: LStr = "", val items: List<QuoteItem> = emptyList(),
    val discountType: LStr = "", val discountValue: LStr = "", val gstPercent: LStr = "",
    /** Feature D (native): wood species this option is priced in (omitted when unset). */
    val species: String? = null,
)

@Serializable
data class Customer(val name: LStr = "", val location: LStr = "")

@Serializable
data class Quote(
    val discountType: LStr = "amount", val discountValue: LStr = "", val gstPercent: LStr = "",
    val notes: List<LStr> = listOf(""),
)

@Serializable
data class Actual(val materials: LStr = "", val labourHours: LStr = "")

/** Whole workspace (`defaultState()`), stored as `mwi-autosave-v1` and in saved-project snapshots. */
@Serializable
data class WorkspaceState(
    val tab: LStr = "calculator",
    val projectName: LStr = "",
    val revision: LInt = 0,
    val projectId: LStr = "",
    val quoteOptions: List<QuoteOption> = emptyList(),
    val activeQuoteOption: LInt = 0,
    val items: List<CalcItem> = listOf(CalcItem.blank()),
    val estimationItems: List<CalcItem> = emptyList(),
    val costings: List<Costing> = emptyList(),
    val quoteItems: List<QuoteItem> = listOf(QuoteItem.blank()),
    val customer: Customer = Customer(),
    val quote: Quote = Quote(),
    val actual: Actual = Actual(),
    /** Feature D (native): print other options as a comparison appendix (null/false = active option only). */
    val printComparison: Boolean? = null,
    /** Feature G (native): print each line's area under its description. */
    val printArea: Boolean? = null,
    /** Feature F (native): estimation item groups (null = ungrouped job, exactly V10.1). */
    val itemGroups: List<ItemGroup>? = null,
) {
    companion object { fun default() = WorkspaceState() }
}

/**
 * Feature F: a group of estimation items (by index). cftRate / kolRate are the default Rate/CFT and Rate/KOL for member
 * items whose own rate is blank; labour / polish / consumables / other are group-level lumpsums; selling is the group's
 * selling price (blank = automatic: members' selling + group lumpsums at cost).
 */
@Serializable
data class ItemGroup(
    val id: LStr = "", val name: LStr = "", val items: List<LInt> = emptyList(),
    val cftRate: LStr = "", val kolRate: LStr = "",
    val labour: LStr = "", val polish: LStr = "", val consumables: LStr = "", val other: LStr = "",
    val selling: LStr = "",
    val collapsed: Boolean? = null,
)

/** Saved project revision record in `mwi-projects-v1`. */
@Serializable
data class SavedProjectRecord(
    val id: LStr = "", val rootId: LStr = "", val revision: LInt = 0, val name: LStr = "",
    val updatedAt: LStr = "", val snapshot: WorkspaceState = WorkspaceState(),
)

/** General CFT/KOL calculator (`mwi-quick-calc-v1`). Group colours are names: red/blue/green/orange/purple. */
@Serializable
data class QuickGroup(
    val id: LStr = "", val rows: List<LInt> = emptyList(), val mode: LStr = "",
    val rate: LStr = "", val color: LStr = "",
)

@Serializable
data class QuickCalc(
    val rows: List<Row> = listOf(Row.blank()), val groups: List<QuickGroup> = emptyList(),
    val cftRate: LStr = "", val kolRate: LStr = "",
)

/** Hoppus log calculator (`mwi-hoppus-v1`). */
@Serializable
data class HoppusLog(val length: LStr = "", val girth: LStr = "")

@Serializable
data class HoppusState(val rows: List<HoppusLog> = listOf(HoppusLog()), val sawRate: LStr = "")

/** Full backup file written/read by Settings › Full Backup & Restore. */
@Serializable
data class BackupFile(
    val format: LStr = "",
    val version: LInt = 0,
    val date: LStr = "",
    val storage: Map<String, String> = emptyMap(),
)

object StorageKeys {
    const val AUTOSAVE = "mwi-autosave-v1"
    const val PROJECTS = "mwi-projects-v1"
    const val SAVED_NOTES = "mwi-saved-notes-v1"
    const val AI_SETTINGS = "mwi-ai-settings-v1"
    const val QUICK_CALC = "mwi-quick-calc-v1"
    const val HOPPUS = "mwi-hoppus-v1"
    const val OPERATIONS = "mwi-operations-v1"
    const val PROJECT_IMPORT_PENDING = "mwi-project-import-pending"
    const val BACKUP_FORMAT = "moras-qe-backup"
    const val BACKUP_VERSION = 1
    const val BACKUP_FILE_NAME = "moras-qe-full-backup.json"
}
