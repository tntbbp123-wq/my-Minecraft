package com.tntbbp.myminecraft.model;

import java.util.Set;
import java.util.UUID;

/** 팀 정보 조회용 뷰. 실제 저장은 TeamManager가 teams.yml에 직접 관리한다. */
public record Team(String name, UUID leader, Set<UUID> members) {
}
