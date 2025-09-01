package com.busanit501.findmyfet.service.post;

import com.busanit501.findmyfet.domain.Role;
import com.busanit501.findmyfet.domain.User;
import com.busanit501.findmyfet.domain.post.Image;
import com.busanit501.findmyfet.domain.post.Post;
import com.busanit501.findmyfet.dto.paging.PageRequestDto;
import com.busanit501.findmyfet.dto.paging.PageResponseDto;
import com.busanit501.findmyfet.dto.post.*;
import com.busanit501.findmyfet.repository.post.ImageRepository;
import com.busanit501.findmyfet.repository.post.PostRepository;
import com.busanit501.findmyfet.repository.UserRepository;
import com.busanit501.findmyfet.repository.CommentRepository;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // 생성자 자동주입
@Log4j2
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final ImageRepository imageRepository;
    private final FileUploadService fileUploadService;
    private final UserRepository  userRepository;
    private final ModelMapper modelMapper;
    private final CommentRepository commentRepository;

    // 페이징처리 + 게시판 조회
    @Override
    @Transactional(readOnly = true)
    // [수정] PageRequestDto -> FindPetSearchCriteria로 변경
    public PageResponseDto<PostListResponseDto> findAllPosts(FindPetSearchCriteria criteria) {
        // [수정] criteria 객체에서 페이징 및 정렬 정보를 가져와 Pageable 객체 생성
        Pageable pageable = PageRequest.of(
                criteria.getPage(),
                criteria.getSize(),
                Sort.by(Sort.Direction.fromString(criteria.getSortDir()), criteria.getSortBy())
        );

        // [수정] Repository의 search 메서드에 criteria 객체를 전달
        Page<Post> result = postRepository.search(criteria, pageable);

        List<PostListResponseDto> dtoList = result.getContent().stream()
                .map(post -> modelMapper.map(post, PostListResponseDto.class))
                .collect(Collectors.toList());

        // [수정] PageResponseDto 생성 로직 변경
        // PageRequestDto는 응답 DTO에서 페이징 UI를 계산할 때만 필요
        PageRequestDto pageRequestDTO = new PageRequestDto();
        pageRequestDTO.setPage(criteria.getPage() + 1);
        pageRequestDTO.setSize(criteria.getSize());

        return PageResponseDto.<PostListResponseDto>withAll()
                .pageRequestDTO(pageRequestDTO)
                .dtoList(dtoList)
                .total((int)result.getTotalElements())
                .build();
    }

    // 전체 게시글리스트 조회기능
//    @Override
//    @Transactional(readOnly = true) // 조회 기능이므로 readOnly=true로 성능 최적화
//    public List<PostListResponseDto> findAllPosts() {
//        return postRepository.findAll().stream() // DB에서 모든 Post를 가져와서
//                .map(PostListResponseDto::new)      // DTO로 변환하고
//                .collect(Collectors.toList());      // List로 만든다.
//    }

    // 게시글 등록기능
    @Override
    @Transactional
    public Long createPost(PostCreateRequestDto requestDto, List<MultipartFile> images, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다. id=" + userId));

        // 1-1. DTO를 Post 엔티티로 변환 후 저장
        Post post = modelMapper.map(requestDto, Post.class);

        post.setUser(user); // 연관관계 설정

        Post savedPost = postRepository.save(post);

        if (images != null && !images.isEmpty()) {
            for (MultipartFile imageFile : images) {
                // 2-1. 실제 파일 업로드 로직 호출
                String storedFilename = fileUploadService.upload(imageFile);
                log.info("Uploaded Image: {}", storedFilename);

                if (storedFilename != null) {
                    // 2-2. Image 엔티티 생성
                    Image image = Image.builder()
                            .imageUrl(storedFilename)
                            .build();

                    savedPost.addImage(image);



                }
            }
        }
        return savedPost.getId();
    }

    // 상세조회기능
    @Override
    @Transactional(readOnly = true)
    public PostDetailResponseDto findPostById(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. id=" + postId));

        PostDetailResponseDto responseDto = modelMapper.map(post, PostDetailResponseDto.class);

//        // 댓글 목록 조회 및 DTO 변환
//        List<com.busanit501.findmyfet.domain.Comment> comments = commentRepository.findByPost_Id(postId);
//        List<com.busanit501.findmyfet.dto.CommentDTO> commentDTOs = comments.stream()
//                .map(comment -> modelMapper.map(comment, com.busanit501.findmyfet.dto.CommentDTO.class))
//                .collect(Collectors.toList());
//        responseDto.setComments(commentDTOs);

        return responseDto;
    }

    // 내 게시글 찾기
    @Override
    @Transactional(readOnly = true)
    public List<MyPostResponseDto> findMyPosts(Long userId) {

        return postRepository.findByUser_UserIdOrderByCreatedAtDesc(userId)
                .stream()
              //[기존] .map(MyPostResponseDto::new)
                .map(post -> modelMapper.map(post, MyPostResponseDto.class))
                .collect(Collectors.toList());
    }

    // 삭제기능
    @Override
    @Transactional
    public void deletePost(Long postId,  Long userId) {
        // 1. 게시글 ID로 Post 엔티티 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. id=" + postId));

        validatePostAuthorOrAdmin(post, userId); // 권한 검사

        // 2. 연관된 이미지 파일들을 서버에서 삭제
        if (post.getImages() != null && !post.getImages().isEmpty()) {
            for (Image image : post.getImages()) {
                String storedFilename = image.getImageUrl();
                log.info("Deleting Image File: {}", storedFilename);
                fileUploadService.delete(storedFilename);
            }
        }

        // 3. Post 엔티티 삭제
        // Post 엔티티에 cascade = CascadeType.ALL, orphanRemoval = true 설정이 되어 있으므로,
        // Post를 삭제하면 연관된 Image 엔티티들도 DB에서 함께 삭제됩니다.
        postRepository.delete(post);
        log.info("Deleted Post ID: {}", postId);
    }

    // 게시글 수정기능
    @Override
    @Transactional
    public void updatePost(Long postId, PostUpdateRequestDto requestDto, List<MultipartFile> newImages, Long userId) {
        // 1. 게시글 ID로 Post 엔티티 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. id=" + postId));

        validatePostAuthorOrAdmin(post, userId); // 권한 검사

        // 2. 기존 이미지 파일 삭제 (간단한 버전: 모든 기존 이미지를 삭제하고 새로 추가)
        if (requestDto.getDeletedImageIds() != null && !requestDto.getDeletedImageIds().isEmpty()) {
            // DB에서 조회한 기존 이미지들의 ID를 Set으로 만들어 빠른 조회를 가능하게 함
            Set<Long> deleteIds = new HashSet<>(requestDto.getDeletedImageIds());

            // Iterator를 사용하여 컬렉션을 순회하면서 안전하게 원소를 제거
            Iterator<Image> iterator = post.getImages().iterator();
            while (iterator.hasNext()) {
                Image image = iterator.next();
                if (deleteIds.contains(image.getId())) {
                    // 2-1. 실제 파일 시스템에서 이미지 파일 삭제
                    fileUploadService.delete(image.getImageUrl());
                    log.info("Deleted image file: {}", image.getImageUrl());

                    // 2-2. 컬렉션에서 Image 엔티티 제거 (orphanRemoval=true에 의해 DB에서도 삭제됨)
                    iterator.remove();
                }
            }
        }
//        post.getImages().clear()가 호출되면, @OneToMany에 설정된 orphanRemoval = true 옵션 덕분에 부모(Post)와의 관계가 끊어진 Image 엔티티들(고아 객체)이 DB에서도 자동으로 삭제


        // 3. 텍스트 정보 업데이트(JPA 더티 체킹 활용)
            // -> 트랜잭션이 끝날 때 변경된 내용을 감지하여 자동으로 UPDATE 쿼리를 실행
        post.update(requestDto);

//        더티 체킹 (Dirty Checking):
//        @Transactional 환경에서 postRepository.findById()로 조회된 post 엔티티는 JPA의 영속성 컨텍스트에 의해 관리됩니다.
//        이 상태에서 post.update(...)와 같이 객체의 상태(필드 값)를 변경하면, 트랜잭션이 끝나는 시점에 JPA가 "어? 처음 조회했을 때랑 지금이랑 상태가 다르네?"라고 감지합니다.
//        이 '더러워진(dirty)' 객체를 발견하면, JPA가 자동으로 UPDATE 쿼리를 생성하여 데이터베이스에 반영해줍니다.
//        따라서 postRepository.save(post)를 다시 호출할 필요가 없어 코드가 간결해집니다.


        // 4. 새로운 이미지 파일 추가
        if (newImages != null && !newImages.isEmpty()) {
            for (MultipartFile imageFile : newImages) {
                String storedFilename = fileUploadService.upload(imageFile);
                if (storedFilename != null) {
                    Image image = Image.builder().imageUrl(storedFilename).build();
                    post.addImage(image);
                    log.info("Added new image: {}", storedFilename);
                }
            }
        }
        // postRepository.save(post)를 호출할 필요가 없습니다.
        // 트랜잭션 커밋 ->  더티 체킹 -> post가 자동으로 DB에 반영
    }

    // 찾기완료 처리하는기능
    @Override
    @Transactional
    public void completePost(Long postId, Long userId) {
        // 1. 게시글 엔티티 조회
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. id=" + postId));

        validatePostAuthorOrAdmin(post, userId); // 권한 검사

        log.info("Completing Post ID: {}", postId);

        // 2. 게시글 상태를 'COMPLETED'로 변경
        post.complete();

        // 3. 더티 체킹에 의해 트랜잭션 종료 시 자동으로 UPDATE 쿼리 실행됨
    }

    //== 검증 로직 (관리자 권한 추가) ==//
    private void validatePostAuthorOrAdmin(Post post, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다. id=" + userId));

        // 현재 로그인한 사용자가 게시글 작성자도 아니고, 관리자도 아니면 예외 발생
        if (!post.getUser().getUserId().equals(userId) && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("해당 게시글에 대한 수정/삭제 권한이 없습니다.");
        }
    }
}