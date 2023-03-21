package solobob.solobobmate.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import solobob.solobobmate.auth.config.SecurityUtil;
import solobob.solobobmate.controller.exception.ErrorCode;
import solobob.solobobmate.controller.exception.SoloBobException;
import solobob.solobobmate.controller.memberDto.MyPageDto;
import solobob.solobobmate.domain.Member;
import solobob.solobobmate.repository.MemberRepository;
import solobob.solobobmate.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import solobob.solobobmate.service.PartyService;

import javax.servlet.http.HttpServletResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class MemberController {

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PartyService partyService;


    public Member getMember() {
        Member member = memberRepository.findByLoginId(SecurityUtil.getCurrentMemberId()).orElseThrow(
                () -> new SoloBobException(ErrorCode.NOT_FOUND_MEMBER)
        );
        return member;
    }

    @GetMapping
    public ResponseEntity myPage() {
        Member member = getMember();
        MyPageDto my = memberService.detail(member);
        return ResponseEntity.ok(my);
    }

    //신뢰도 업
    @PostMapping("/{member_id}/like")
    public void memberLike(@PathVariable(name = "member_id") Long id) {
        Member member = memberRepository.findById(id).orElseThrow(
                () -> new SoloBobException(ErrorCode.NOT_FOUND_MEMBER)
        );
        memberService.likeUp(member);
    }

    @GetMapping("/myParty")
    public ResponseEntity myParty() {
        Member member = getMember();

        memberRepository.findByLoginIdWithParty(member.getLoginId()).orElseThrow(
                () -> new SoloBobException(ErrorCode.PARTY_MY_PARTY)
        );

        return ResponseEntity.ok(partyService.myPartyInfo(member));
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {

        ResponseCookie cookie = ResponseCookie.from("RefreshToken", null)
                .maxAge(0)
                .path("/")
                .sameSite("None")  //빌드 후 배포 시엔 수정 예정
                .secure(true)
                .httpOnly(true)
                .build();

        response.setHeader("Set-Cookie", cookie.toString());
    }


}
