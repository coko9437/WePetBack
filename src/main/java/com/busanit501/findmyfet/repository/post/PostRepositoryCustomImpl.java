package com.busanit501.findmyfet.repository.post;

import com.busanit501.findmyfet.domain.post.*;
import com.busanit501.findmyfet.dto.post.FindPetSearchCriteria;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> search(FindPetSearchCriteria criteria, Pageable pageable) {
        QPost post = QPost.post;
        BooleanBuilder booleanBuilder = new BooleanBuilder();

        // ===== String 타입 필드 검색 (DTO 헬퍼 메서드 사용) =====
        if (criteria.hasTitle()) {
            booleanBuilder.and(post.title.contains(criteria.getTitle()));
        }

        // ✅ [추가] 작성자 이름(authorName)으로 검색하는 로직
        if (criteria.hasAuthorName()) {
            booleanBuilder.and(post.user.name.contains(criteria.getAuthorName()));
        }

        if (criteria.hasLocation()) {
            booleanBuilder.and(post.location.contains(criteria.getLocation()));
        }
        if (criteria.hasAnimalCategory()) {
            booleanBuilder.and(post.animalCategory.equalsIgnoreCase(criteria.getAnimalCategory().trim()));
        }
        if (criteria.hasAnimalBreed()) {
            booleanBuilder.and(post.animalBreed.equalsIgnoreCase(criteria.getAnimalBreed().trim()));
        }

        // ===== Enum 타입 필드 검색 (문자열을 Enum으로 변환하여 비교) =====
        if (StringUtils.hasText(criteria.getPostType())) {
            booleanBuilder.and(post.postType.eq(PostType.valueOf(criteria.getPostType().toUpperCase())));
        }
        if (StringUtils.hasText(criteria.getStatus())) {
            booleanBuilder.and(post.status.eq(Status.valueOf(criteria.getStatus().toUpperCase())));
        }

        // ✅ [개선] StringUtils -> hasGender() 헬퍼 메서드로 변경 (일관성)
        if (criteria.hasGender()) {
            booleanBuilder.and(post.gender.eq(AnimalGender.valueOf(criteria.getGender().toUpperCase())));
        }

        // ===== 날짜 범위 검색 =====
        if (criteria.getLostTimeFrom() != null) {
            booleanBuilder.and(post.lostTime.goe(criteria.getLostTimeFrom()));
        }
        if (criteria.getLostTimeTo() != null) {
            booleanBuilder.and(post.lostTime.loe(criteria.getLostTimeTo()));
        }

        // ✅ pageable 객체로부터 페이징 정보(offset, limit)를 얻어 쿼리에 적용
        JPAQuery<Post> query = queryFactory.selectFrom(post)
                .leftJoin(post.user).fetchJoin()
                .where(booleanBuilder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc());

        List<Post> content = query.fetch();

        // ✅ 전체 데이터 개수 카운트 쿼리 (페이지네이션 계산용)
        JPAQuery<Long> countQuery = queryFactory.select(post.count())
                .from(post)
                .where(booleanBuilder);

        long total = countQuery.fetchOne() != null ? countQuery.fetchOne() : 0L;

        return new PageImpl<>(content, pageable, total);
    }
}