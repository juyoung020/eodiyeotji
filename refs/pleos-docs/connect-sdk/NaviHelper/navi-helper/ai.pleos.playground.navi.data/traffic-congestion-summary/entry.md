> 출처: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/entry

# TrafficCongestionSummary

@Serializable

data class [TrafficCongestionSummary](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/entry)(val totalCongestionDist: [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-double/index.html), val totalCongestionTime: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), val totalCongestionAvrSpeed: [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-double/index.html))

Data class representing a summary of traffic congestion.

## Constructors

[TrafficCongestionSummary](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/-traffic-congestion-summary)

Link copied to clipboard

constructor(totalCongestionDist: [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-double/index.html), totalCongestionTime: [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html), totalCongestionAvrSpeed: [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-double/index.html))

## Properties

[totalCongestionAvrSpeed](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-avr-speed)

Link copied to clipboard

val [totalCongestionAvrSpeed](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-avr-speed): [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-double/index.html)

The average speed across all congested areas (in km/h).

[totalCongestionDist](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-dist)

Link copied to clipboard

val [totalCongestionDist](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-dist): [Double](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-double/index.html)

The total distance of all congested areas (in meters).

[totalCongestionTime](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-time)

Link copied to clipboard

val [totalCongestionTime](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/traffic-congestion-summary/total-congestion-time): [Long](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin-stdlib/kotlin/-long/index.html)

The total duration of traffic congestion (in seconds).
