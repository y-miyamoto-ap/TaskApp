package jp.techacademy.yuki.miyamoto3.taskapp

import java.io.Serializable
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

open class Task : RealmObject, Serializable {
    @PrimaryKey
    var id = 0

    var title = "" // タイトル
    var contents = "" // 内容
    var date = "" // 日時
    var category = "" // カテゴリ
}