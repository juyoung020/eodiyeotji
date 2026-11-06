package app.eodiyeotji.ui.agent

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 기억 에이전트 화면 연결: 화면 → 실제 기억 에이전트(Rust `MemoryAgent`, [MemoryAgentPort]).
 * 주행 상태([DrivingStatePort])는 판마다 다르다 → `platform/PlatformModule` (car·phone 소스 세트).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentTempModule {
    @Binds
    abstract fun bindAgent(impl: MemoryAgentPort): AgentPort
}
