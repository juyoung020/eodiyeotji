package app.eodiyeotji.ui.agent

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 임시 연결. 진짜 기억 에이전트·주행 상태가 들어오기 전까지 화면을 만들고 시연하려고 가짜를 꽂는다.
 *
 * TODO(통합): MemoryAgent로 교체 — `bindAgent`를 실제 에이전트(AgentPort 구현)로 바꾸고,
 *  `bindDrivingState`를 CarUxRestrictions 구현으로 바꾼 뒤 이 모듈을 지운다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentTempModule {
    @Binds
    abstract fun bindAgent(impl: FakeAgentPort): AgentPort

    @Binds
    abstract fun bindDrivingState(impl: ManualDrivingStatePort): DrivingStatePort
}
