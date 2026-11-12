package app.eodiyeotji.core

import androidx.compose.runtime.Immutable
import app.eodiyeotji.core.rust.GalleryPhoto as RGalleryPhoto
import app.eodiyeotji.core.rust.GeoPoint as RGeoPoint
import app.eodiyeotji.core.rust.MatchedPhoto as RMatchedPhoto
import app.eodiyeotji.core.rust.MapPhotoSpot as RMapPhotoSpot
import app.eodiyeotji.core.rust.StopPhotoRef as RStopPhotoRef

/*
 * 내 사진(기기 갤러리) 연동 입구 (core-rs/src/gallery.rs). 사진을 읽는 건 gallery/GalleryReader.kt,
 * 머문 곳에 맞추기 · 순서 · 집/회사 근처 거르기 · 올린 글에 붙인 사진 저장은 Rust. 여기는 옮겨 담기만 한다.
 */

/** 갤러리에서 읽은 사진 한 장 (Rust로 넘길 원재료). [lat]·[lon]은 EXIF GPS, 없으면 null. */
data class GalleryItem(val id: Long, val uri: String, val takenMs: Long, val lat: Double?, val lon: Double?)

/** 들른 곳에 맞춘 내 사진. [shareable]이 false면 집·회사 근처라 올리기 후보가 아니다. */
@Immutable
data class MyPhoto(val id: Long, val uri: String, val takenMs: Long, val shareable: Boolean)

/** 머문 곳 하나의 내 사진 (대표 순, [total]은 전체 수 → "+N"). [arriveMs] = 머문 곳 도착 시각 (항목 키 `s<도착>`). */
@Immutable
data class StayMyPhotos(val arriveMs: Long, val photos: List<MyPhoto>, val total: Int)

/** 그날 갤러리에서 읽을 찍은 시각 범위 (머문 곳이 없으면 null). */
suspend fun EodiCore.galleryWindow(day: String): LongRange? = coreCall("galleryWindow") {
    val w = rust.galleryWindow(day)
    if (w.fromMs > w.toMs) null else w.fromMs..w.toMs
}

/** 사진 묶음을 그날 머문 곳마다 나눈다 (한 번에 Rust로). 도착 시각 → 사진. */
suspend fun EodiCore.galleryForDay(day: String, items: List<GalleryItem>): Map<Long, StayMyPhotos> = coreCall("galleryForDay") {
    rust.galleryForDay(day, items.map { it.toR() }).associate { s ->
        s.arriveMs to StayMyPhotos(s.arriveMs, s.photos.map { it.toModel() }, s.total.toInt())
    }
}

/** 지도에 꽂을 내 사진 (찍은 곳, 위치가 없으면 맞춘 머문 곳 가운데). [stayArriveMs]가 null이면 길 위에서 찍은 사진. */
@Immutable
data class MapPhotoSpot(val id: Long, val uri: String, val takenMs: Long, val lat: Double, val lon: Double, val stayArriveMs: Long?)

/** 그날 사진을 지도에 꽂을 자리로 (찍은 시각순). */
suspend fun EodiCore.galleryMapSpots(day: String, items: List<GalleryItem>): List<MapPhotoSpot> = coreCall("galleryMapSpots") {
    rust.galleryMapSpots(day, items.map { it.toR() }).map { it.toModel() }
}

/** 주행 하나 동안 찍은 사진 (드라이브 코스 후보, 최신순). */
suspend fun EodiCore.galleryForTrip(startMs: Long, endMs: Long, items: List<GalleryItem>): List<MyPhoto> = coreCall("galleryForTrip") {
    rust.galleryForTrip(startMs, endMs, items.map { it.toR() }).map { it.toModel() }
}

/** 내가 올린 여행 코스 후기의 들른 곳 사진 바꾸기 (도착 시각 → 앱 안 사본 경로). 바뀐 글을 돌려준다. */
suspend fun EodiCore.setCourseStopPhotos(postId: String, photos: Map<Long, String>): DriveCourse = coreCall("setCourseStopPhotos") {
    rust.setCourseStopPhotos(postId, photos.map { (t, p) -> RStopPhotoRef(t, p) }).toDrive()
}

/** 내가 올린 드라이브 코스의 사진 바꾸기. */
suspend fun EodiCore.setDriveRoutePhotos(id: String, paths: List<String>) = coreCall("setDriveRoutePhotos") {
    rust.setDriveRoutePhotos(id, paths)
}

private fun GalleryItem.toR() = RGalleryPhoto(id, uri, takenMs, if (lat != null && lon != null) RGeoPoint(lat, lon) else null)

private fun RMapPhotoSpot.toModel() = MapPhotoSpot(id, uri, takenMs, at.lat, at.lon, stayArriveMs)

private fun RMatchedPhoto.toModel() = MyPhoto(photo.id, photo.uri, photo.takenMs, shareable)
