package com.samify.music.models

import com.metrolist.innertube.models.YTItem
import com.samify.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
