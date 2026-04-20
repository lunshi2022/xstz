package com.huaying.xstz.data.converter

import androidx.room.TypeConverter
import com.huaying.xstz.data.entity.AssetType

/**
 * TypeConverter for AssetType enum to handle database storage and retrieval
 */
class AssetTypeConverter {

    @TypeConverter
    fun fromAssetType(value: AssetType): String {
        return value.name
    }

    @TypeConverter
    fun toAssetType(value: String): AssetType {
        return try {
            AssetType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Handle legacy GOLD value by mapping to COMMODITY
            if (value == "GOLD") {
                AssetType.COMMODITY
            } else {
                // Default to STOCK for unknown values
                AssetType.STOCK
            }
        }
    }
}
