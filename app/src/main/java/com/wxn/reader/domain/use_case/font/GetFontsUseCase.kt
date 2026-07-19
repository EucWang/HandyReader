package com.wxn.reader.domain.use_case.font

import com.wxn.reader.data.dto.FontEntity
import com.wxn.reader.data.dto.FontFileEntity
import com.wxn.reader.data.model.FontCatalogItem
import com.wxn.reader.data.model.FontVariantItem
import com.wxn.reader.domain.repository.FontRepository
import com.wxn.reader.util.FontFamilyAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject

data class FontListItem(
    val catalogItem: FontCatalogItem,
    val fontEntity: FontEntity? = null,
    val downloadedFiles: List<FontFileEntity> = emptyList(),
    val isDownloaded: Boolean = false,
    val downloadedCount: Int = 0,
    val totalVariants: Int = 0,
    val localDir: String? = null,
    val source: String = "download"
)

class GetFontsUseCase @Inject constructor(
    private val fontRepository: FontRepository
) {

    operator fun invoke(): Flow<List<FontListItem>> = flow {
        val catalog = fontRepository.getCatalog()
        val catalogIds = catalog.map { it.id }.toSet()
        combine(
            fontRepository.getAllFonts(),
            fontRepository.getDownloadedFiles()
        ) { fonts, downloadedFiles ->
            val fontMap = fonts.associateBy { it.id }
            val filesByFont = downloadedFiles.groupBy { it.fontId }

            val catalogItems = catalog.map { item ->
                val entity = fontMap[item.id]
                val files = filesByFont[item.id].orEmpty()
                val totalVariants = item.variants.size
                val localDir = entity?.localDir
                val dirExists = localDir != null && File(localDir).exists()
                        && File(localDir).listFiles()?.isNotEmpty() == true
                FontListItem(
                    catalogItem = item.copy(variants = item.variants.sortedBy { FontFamilyAnalyzer.variantWeight(it.variant) }),
                    fontEntity = entity,
                    downloadedFiles = files,
                    isDownloaded = dirExists,
                    downloadedCount = files.size,
                    totalVariants = totalVariants,
                    localDir = localDir,
                    source = "download"
                )
            }

            val importedItems = fonts
                .filter { it.id !in catalogIds && it.source == "import" }
                .map { entity ->
                    val files = filesByFont[entity.id].orEmpty()
                    val localDir = entity.localDir
                    val dirExists = localDir != null && File(localDir).exists()
                            && File(localDir).listFiles()?.isNotEmpty() == true
                    val syntheticCatalogItem = FontCatalogItem(
                        id = entity.id,
                        displayName = entity.displayName,
                        category = entity.category,
                        language = entity.language,
                        dirName = entity.dirName,
                        variants = files.map { file ->
                            FontVariantItem(
                                variant = file.variant,
                                name = file.name,
                                url = "",
                                fileName = file.fileName,
                                localFileName = file.localFileName
                            )
                        }.sortedBy { FontFamilyAnalyzer.variantWeight(it.variant) }
                    )
                    FontListItem(
                        catalogItem = syntheticCatalogItem,
                        fontEntity = entity,
                        downloadedFiles = files,
                        isDownloaded = dirExists,
                        downloadedCount = files.size,
                        totalVariants = files.size,
                        localDir = localDir,
                        source = "import"
                    )
                }

            catalogItems + importedItems
        }.collect { emit(it) }
    }
}
