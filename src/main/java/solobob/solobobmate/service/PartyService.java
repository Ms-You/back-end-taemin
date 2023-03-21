package solobob.solobobmate.service;

import solobob.solobobmate.controller.exception.ErrorCode;
import solobob.solobobmate.controller.exception.SoloBobException;
import solobob.solobobmate.controller.partyDto.PartyDto;
import solobob.solobobmate.controller.reportDto.ReportDto;
import solobob.solobobmate.domain.*;
import solobob.solobobmate.repository.ChatRoomRepository;
import solobob.solobobmate.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomService chatRoomService;
    private final ReportService reportService;


    //파티 생성
    @Transactional
    public Party create(Member member, Restaurant restaurant, String title, int maxNumber) {
        if (member.getIsJoined()) {
            throw new SoloBobException(ErrorCode.PARTY_CREATE);
        }
        ChatRoom chatRoom = chatRoomService.create();
        Party party = Party.create(member, restaurant, chatRoom, title, maxNumber);
        partyRepository.save(party);
        return party;
    }


    // 파티 수정
    @Transactional
    public void update(Party party, String title, int maxNumber) {
        if (party.getMembers().size() > maxNumber) {
            throw new SoloBobException(ErrorCode.PARTY_UPDATE);
        }
        party.update(title, maxNumber);
    }


    //파티 참가
    @Transactional
    public PartyDto join(Party party, Member member) {
        if (party.getFullStatus() == FullStatus.FULL) {
            throw new SoloBobException(ErrorCode.PARTY_JOIN_FULL);
        } else if (member.getIsJoined()) {
            throw new SoloBobException(ErrorCode.PARTY_JOIN_JOINED);
        } else if (party.getMatchingStatus() == MatchingStatus.MATCHED) {
            throw new SoloBobException(ErrorCode.PARTY_JOIN_MATCHED);
        }
        party.addMember(member);

        return partyInfoReturn(party);
    }


    // 파티 나가기 (멤버만 가능, 방장x)
    @Transactional
    public void exit(Party party, Member member, Member owner) {
        if (!member.getIsJoined()){
            throw new SoloBobException(ErrorCode.PARTY_MY_PARTY);
        } else if (member.getOwner()) {
            throw new SoloBobException(ErrorCode.PARTY_EXIT_OWNER);
        } else if (party.getMatchingStatus() == MatchingStatus.MATCHED) {
            // 매칭 상태에서 나가면 신고 1스택 쌓임 (탈주항목으로 신고한 것으로 됨)
            reportService.reportMember(owner, member, party, new ReportDto(member.getId(), ReportType.ESCAPE, "탈주로 인해 자동으로 신고되었습니다."));
        }
        party.deleteMember(member);
    }


    @Transactional
    public void startOrReady(Party party, Member member) {
        if (member.getParty().getId() != party.getId()){
            throw new SoloBobException(ErrorCode.PARTY_MY_PARTY);
        }

        if (member.getOwner()){  //방장인 경우
            if (member.getIsReady()) {  // 파티 시작 취소
                party.startCancelParty(member);
            }else{  // 파티 시작
                if (party.getMembers().size() >= 2) {  // 방장 포함 인원수 2 이상일때 가능
                    if (readyStatus(party)){
                        party.startParty(member);
                    }else{
                        throw new SoloBobException(ErrorCode.PARTY_SOR_READY);
                    }
                }else{
                    throw new SoloBobException(ErrorCode.PARTY_SOR_NUM);
                }
            }
        }else{      // 방장 아닌 경우
            if (member.getIsReady()) {  //파티 준비 취소
                if (party.getMatchingStatus() == MatchingStatus.MATCHED) {  //이미 시작이 된 경우
                    throw new SoloBobException(ErrorCode.PARTY_SOR_MATCH);
                }else{  //파티 준비 취소 작업 가능
                    party.cancelReady(member);
                }
            }else{  //파티 준비 작업
                party.ready(member);
            }
        }
    }


    //파티원이 모두 준비상태인지 확인 메소드 (방장을 제외한)
    public Boolean readyStatus(Party party) {
        for (Member member : party.getMembers()) {
            if (member.getOwner()) continue;
            if (!member.getIsReady()) return false;
        }
        return true;
    }


    //내 파티 정보
    public PartyDto myPartyInfo(Member member) {
        if (member.getParty() == null) {
            throw new SoloBobException(ErrorCode.PARTY_MY_PARTY);
        }
        return partyInfoReturn(member.getParty());
    }


    //파티생성, 참가 시 파티정보(참가자 목록) 반환 메소드
    public PartyDto partyInfoReturn(Party party) {
        return new PartyDto(party);
    }


    //파티 삭제 (방장 권한)
    @Transactional
    public void initialMembers(Member member, Party party) {
        if (!member.getOwner()) {
            throw new SoloBobException(ErrorCode.PARTY_DELETE_OWNER);
        }
        for (Member partyMember : party.getMembers()) {
            if (partyMember.getOwner()) { //프록시 객체 초기화 => (1)+1
                partyMember.setOwner(false);
            }
            partyMember.setParty(null);
            partyMember.setIsJoined(false);
            partyMember.setIsReady(false);
        }
        party.getMembers().clear();
        party.getChatRoom().setParty(null);
        party.getChatRoom().setParty(null);
        party.getChatRoom().getChats().clear();

        chatRoomRepository.delete(party.getChatRoom());

        partyRepository.removeParty(party);

    }


    // 파티원 강퇴 (방장 권한 - 매칭 되어도 강퇴 가능)
    @Transactional
    public void kickOutMember(Member member, Member kickMember, Party party){
        if (!member.getOwner()) {
            throw new SoloBobException(ErrorCode.PARTY_DELETE_OWNER);
        }
        if (!party.getMembers().contains(kickMember)){
            throw new SoloBobException(ErrorCode.PARTY_MY_PARTY);
        }

        party.deleteMember(kickMember);
    }


//    public void removePartyScheduler() {
//        List<Party> parties = partyRepository.findAllWithMembers();
//        for (Party party : parties) {
//            if (party.getMatchingStatus() == MatchingStatus.MATCHED) {
//                Duration duration = Duration.between(party.getMatchingStartTime(), LocalDateTime.now());
//                if (duration.getSeconds() >= 7200) {   //2시간 지나면
//                    initialMembers(party);
//                }
//            }
//        }
//    }


}
