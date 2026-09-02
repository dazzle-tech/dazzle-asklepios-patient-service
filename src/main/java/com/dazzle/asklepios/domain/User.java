package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.config.Constants;
import com.dazzle.asklepios.domain.enumeration.JobRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "app_user")
@Data
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = 1, max = 50)
    private String login;

    @NotNull
    private boolean activated = false;

    @Size(max = 50)
    @Column(name="first_name")
    private String firstName;

    @Size(max = 50)
    @Column(name="last_name")
    private String lastName;

    @NotBlank(message = "Email can not to be Null")
    @Email
    @Size(min = 5, max = 254)
    private String email;

    @Size(max = 20)
    @Column(name="phone_number")
    private String phoneNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "job_role")
    private JobRole jobRole;

    @Size(min = 2, max = 10)
    @Column(name="lang_key")
    private String langKey;

    @Column(name="allow_ongoing_visit")
    private boolean allowOngoingVisit;

    @Column(name="can_un_discharge_urgent_care")
    private boolean canUnDischargeUrgentCare;

    @Column(name="can_un_complete_encounter")
    private boolean canUnCompleteEncounter;
}
