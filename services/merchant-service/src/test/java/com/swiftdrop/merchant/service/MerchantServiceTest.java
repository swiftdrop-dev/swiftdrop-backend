package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantException;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.security.*;
import jakarta.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MerchantServiceTest {
 static ValidatorFactory factory;
 MerchantRepository repository;
 MerchantIdentityResolver identity;
 MerchantService service;
 Merchant merchant;
 @BeforeAll static void validators() { factory=Validation.buildDefaultValidatorFactory(); }
 @AfterAll static void close() { factory.close(); }
 @BeforeEach void setup() {
  repository=mock(MerchantRepository.class); identity=mock(MerchantIdentityResolver.class);
  when(identity.currentActor()).thenReturn(new MerchantActor("owner", Set.of()));
  service=new MerchantService(repository, identity, factory.getValidator());
  merchant=new Merchant("owner","Shop","REG","Contact","a@example.com","+94112345678","Street","City","District");
  when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
 }
 MerchantOnboardingRequest request() { return new MerchantOnboardingRequest("Shop","REG","Contact","a@example.com","+94112345678","Street","City","District"); }
 MerchantProfileUpdateRequest update() { return new MerchantProfileUpdateRequest("Updated","a@example.com","+94112345678","Street","City","District"); }
 void failure(int status, Runnable call) { assertEquals(status, assertThrows(MerchantException.class, call::run).getStatus()); }
 void reviewer() { when(identity.currentActor()).thenReturn(new MerchantActor("reviewer", Set.of(MerchantService.REVIEW_AUTHORITY))); }
 @Test void registrationUsesVerifiedOwnerAndPendingStatus() {
  assertEquals(MerchantStatus.PENDING,service.register(request()).status());
  verify(repository).saveAndFlush(argThat(m -> m.getOwnerSubject().equals("owner")));
 }
 @Test void duplicateRegistrationConflicts() { when(repository.existsByOwnerSubject("owner")).thenReturn(true); failure(409,()->service.register(request())); verify(repository,never()).saveAndFlush(any()); }
 @Test void profileIsOwnerScoped() { when(repository.findByOwnerSubject("owner")).thenReturn(Optional.of(merchant)); assertEquals(merchant.getId(),service.profile().id()); verify(repository).findByOwnerSubject("owner"); }
 @Test void missingOwnedProfileIs404() { failure(404,()->service.profile()); verify(repository,never()).findById(any()); }
 @Test void pendingCannotUpdate() { when(repository.findByOwnerSubject("owner")).thenReturn(Optional.of(merchant)); failure(403,()->service.update(update())); }
 @Test void rejectedCannotUpdate() { merchant.review(MerchantStatus.REJECTED,"reviewer","reason"); when(repository.findByOwnerSubject("owner")).thenReturn(Optional.of(merchant)); failure(403,()->service.update(update())); }
 @Test void approvedUpdatesPreserveIdentityAndStatus() {
  merchant.review(MerchantStatus.APPROVED,"reviewer",null); when(repository.findByOwnerSubject("owner")).thenReturn(Optional.of(merchant));
  var result=service.update(update()); assertEquals("Updated",result.contactName()); assertEquals("Shop",result.businessName()); assertEquals(MerchantStatus.APPROVED,result.status()); assertEquals("owner",merchant.getOwnerSubject());
 }
 @Test void reviewRequiresAuthorityBeforeLookup() { failure(403,()->service.review(merchant.getId(),new MerchantReviewRequest(MerchantStatus.APPROVED,null))); verifyNoInteractions(repository); }
 @Test void selfReviewForbidden() {
  when(identity.currentActor()).thenReturn(new MerchantActor("owner",Set.of(MerchantService.REVIEW_AUTHORITY))); when(repository.findById(merchant.getId())).thenReturn(Optional.of(merchant));
  failure(403,()->service.review(merchant.getId(),new MerchantReviewRequest(MerchantStatus.APPROVED,null)));
 }
 @Test void authorizedApproval() { reviewer(); when(repository.findById(merchant.getId())).thenReturn(Optional.of(merchant)); assertEquals(MerchantStatus.APPROVED,service.review(merchant.getId(),new MerchantReviewRequest(MerchantStatus.APPROVED,null)).status()); assertNotNull(merchant.getReviewedAt()); }
 @Test void rejectionRecordsReason() { reviewer(); when(repository.findById(merchant.getId())).thenReturn(Optional.of(merchant)); assertEquals("reason",service.review(merchant.getId(),new MerchantReviewRequest(MerchantStatus.REJECTED," reason ")).reviewReason()); assertEquals(MerchantStatus.REJECTED,merchant.getStatus()); }
 @Test void rejectionRequiresReason() { reviewer(); failure(400,()->service.review(merchant.getId(),new MerchantReviewRequest(MerchantStatus.REJECTED," "))); verifyNoInteractions(repository); }
 @Test void pendingIsInvalidDecision() { reviewer(); failure(400,()->service.review(merchant.getId(),new MerchantReviewRequest(MerchantStatus.PENDING,null))); }
 @Test void repeatReviewConflicts() { reviewer(); merchant.review(MerchantStatus.APPROVED,"other",null); when(repository.findById(merchant.getId())).thenReturn(Optional.of(merchant)); failure(409,()->service.review(merchant.getId(),new MerchantReviewRequest(MerchantStatus.REJECTED,"reason"))); }
 @Test void missingReviewTargetIs404() { reviewer(); failure(404,()->service.review(UUID.randomUUID(),new MerchantReviewRequest(MerchantStatus.APPROVED,null))); }
 @Test void invalidRegistrationDoesNotWrite() { failure(400,()->service.register(new MerchantOnboardingRequest(" ","REG","Contact","bad","x","Street","City","District"))); verifyNoInteractions(repository); }
 @Test void unavailableIdentityFailsAllOperationsClosed() {
  service=new MerchantService(repository,new UnavailableMerchantIdentityResolver(),factory.getValidator());
  failure(503,()->service.register(request())); failure(503,()->service.profile()); failure(503,()->service.update(update())); failure(503,()->service.review(merchant.getId(),new MerchantReviewRequest(MerchantStatus.APPROVED,null))); verifyNoInteractions(repository);
 }
 @Test void nullActorFailsClosed() { when(identity.currentActor()).thenReturn(null); failure(503,()->service.profile()); verifyNoInteractions(repository); }
}
