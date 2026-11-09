package app.eodiyeotji.platform

/**
 * 실내 무드등 연출: "그날로 돌아가기"를 보는 동안 무드등을 그날 하늘빛(새벽 · 낮 · 노을 · 푸른 시간 · 밤)으로 잠깐 물들이고,
 * 닫으면 원래 색으로 되돌린다. 차는 Pleos Vehicle SDK `Light.setAmbientLightBrightnessColor`, 폰은 아무것도 하지 않는다.
 * 정차 중에만 쓴다 (다시 보기는 주행이 시작되면 닫힌다).
 */
interface MoodLight {
    /** 무드등을 [argb] 색으로 (처음 부를 때 원래 색을 기억). 지원하지 않으면 조용히 넘어간다. */
    suspend fun tint(argb: Int)

    /** 기억해 둔 원래 색으로 되돌린다. */
    suspend fun restore()
}
