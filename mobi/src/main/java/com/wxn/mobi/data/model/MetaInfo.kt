package com.wxn.mobi.data.model

data class MetaInfo(
    var title: String,
    var author: String,
    var contributor: String,

    var subject: String,
    var publisher:String,
    var date:String,

    var description: String,
    var review: String,
    var imprint: String,

    var copyright: String,
    var isbn: String,
    var asin: String,

    var language: String,
    var isEncrypted:Boolean,

    var coverPath:String
)