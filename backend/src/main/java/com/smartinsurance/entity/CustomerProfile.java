package com.smartinsurance.entity;

import com.smartinsurance.enums.KycStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {
    @Id
    private Long userId;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "address_encrypted", columnDefinition = "text")
    private String addressEncrypted;
    @Column(name = "phone_encrypted", columnDefinition = "text")
    private String phoneEncrypted;
    @Column(name = "national_id_encrypted", columnDefinition = "text")
    private String nationalIdEncrypted;
    @Enumerated(EnumType.STRING) @Column(name = "kyc_status", nullable = false, length = 30)
    private KycStatus kycStatus = KycStatus.PENDING;
    protected CustomerProfile() { }
    public CustomerProfile(AppUser user) { this.user = user; }
    public Long getUserId() { return userId; }
    public AppUser getUser() { return user; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getAddressEncrypted() { return addressEncrypted; }
    public String getPhoneEncrypted() { return phoneEncrypted; }
    public String getNationalIdEncrypted() { return nationalIdEncrypted; }
    public KycStatus getKycStatus() { return kycStatus; }
    public void update(LocalDate dob, String address, String phone, String nationalId) {
        this.dateOfBirth = dob; this.addressEncrypted = address; this.phoneEncrypted = phone; this.nationalIdEncrypted = nationalId;
        this.kycStatus = KycStatus.PENDING;
    }
    public void setKycStatus(KycStatus status) { this.kycStatus = status; }
}
