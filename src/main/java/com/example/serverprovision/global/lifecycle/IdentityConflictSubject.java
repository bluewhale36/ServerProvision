package com.example.serverprovision.global.lifecycle;

/**
 * 정체 충돌 안내 문장의 재료(HF30 D2) — 어떤 자원의 무엇이 겹쳤는가. {@link LifecycleStage} 가 상태별 다음 행동 문장을 만들 때
 * 자원 이름을 인자로 받기 위한 값이다(enum 안에 자원별 분기를 두지 않는다).
 *
 * <p>서버 쪽에는 받침에 따라 조사를 고르는 도구가 없으므로, 문장에 들어가는 꼴을 자원이 직접 준다
 * (예: "메인보드 모델" · "메인보드 모델이" · "모델을" / "RAID 카드" · "RAID 카드가" · "카드를").</p>
 *
 * @param noun           자원 이름("메인보드 모델") — "이미 등록된 {noun}입니다"
 * @param nounAsSubject  주격 꼴("메인보드 모델이") — "같은 {nounAsSubject} 있습니다"
 * @param shortAsObject  짧은 목적격 꼴("모델을") — "휴지통의 {shortAsObject} 영구 삭제하십시오"
 * @param identity       겹친 정체를 사람이 읽는 글("Asus P13R-E")
 */
public record IdentityConflictSubject(String noun, String nounAsSubject, String shortAsObject, String identity) {
}
