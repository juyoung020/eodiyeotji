package app.eodiyeotji.ui.agent

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 기억 에이전트 화면 연결: 화면 → 실제 기억 에이전트(Rust `MemoryAgent`, [MemoryAgentPort]).
 * 주행 상태: AAOS 운전자 주의 분산 제약([CarDrivingStatePort]).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentTempModule {
    @Binds
    abstract fun bindAgent(impl: MemoryAgentPort): AgentPort

    @Binds
    abstract fun bindDrivingState(impl: CarDrivingStatePort): DrivingStatePort
}
