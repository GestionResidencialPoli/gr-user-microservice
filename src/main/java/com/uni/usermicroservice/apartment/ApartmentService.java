package com.uni.usermicroservice.apartment;

import com.uni.usermicroservice.identity.domain.Apartment;
import com.uni.usermicroservice.identity.domain.ApartmentRepository;
import com.uni.usermicroservice.identity.domain.Owner;
import com.uni.usermicroservice.identity.domain.OwnerRepository;
import com.uni.usermicroservice.identity.domain.ResidentUserService;
import com.uni.usermicroservice.identity.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApartmentService {

    private final ApartmentRepository apartmentRepository;
    private final OwnerRepository ownerRepository;
    private final ResidentUserService residentUserService;

    public ApartmentService(
            ApartmentRepository apartmentRepository,
            OwnerRepository ownerRepository,
            ResidentUserService residentUserService
    ) {
        this.apartmentRepository = apartmentRepository;
        this.ownerRepository = ownerRepository;
        this.residentUserService = residentUserService;
    }

    @Transactional
    public ApartmentResponse create(ApartmentRequest request) {
        String torre = trimmed(request.torre());
        String numero = trimmed(request.numero());

        if (apartmentRepository.existsByTorreAndNumero(torre, numero)) {
            throw new ApartmentAlreadyExistsException(torre, numero);
        }

        Apartment apartment = new Apartment(
                torre,
                numero,
                request.piso(),
                request.coeficienteCopropiedad(),
                request.area()
        );
        apartmentRepository.save(apartment);

        User owner = resolveOwner(request.propietario());
        ownerRepository.save(new Owner(owner, apartment, true));

        return ApartmentResponse.from(apartment, PropietarioResponse.from(owner));
    }

    @Transactional(readOnly = true)
    public ApartmentResponse findById(Long id) {
        Apartment apartment = apartmentRepository.findById(id)
                .orElseThrow(() -> new ApartmentNotFoundException(id));

        return ApartmentResponse.from(apartment, principalOwnerResponseOf(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApartmentResponse> search(String torre, String numero, Pageable pageable) {
        Page<Apartment> apartments = apartmentRepository.search(likePatternOf(torre), likePatternOf(numero), pageable);

        Map<Long, PropietarioResponse> ownersByApartmentId = principalOwnersOf(apartments.getContent());
        Page<ApartmentResponse> responses = apartments.map(
                apartment -> ApartmentResponse.from(apartment, ownersByApartmentId.get(apartment.getId()))
        );

        return PageResponse.from(responses);
    }

    @Transactional
    public ApartmentResponse update(Long id, ApartmentRequest request) {
        Apartment apartment = apartmentRepository.findById(id)
                .orElseThrow(() -> new ApartmentNotFoundException(id));

        String torre = trimmed(request.torre());
        String numero = trimmed(request.numero());

        if (apartmentRepository.existsByTorreAndNumeroAndIdNot(torre, numero, id)) {
            throw new ApartmentAlreadyExistsException(torre, numero);
        }

        apartment.setTorre(torre);
        apartment.setNumero(numero);
        apartment.setPiso(request.piso());
        apartment.setCoeficienteCopropiedad(request.coeficienteCopropiedad());
        apartment.setArea(request.area());

        User owner = ownerRepository.findByApartmentIdAndPrincipalTrue(id)
                .map(existing -> updatableOwnerOf(id, existing.getUser(), request.propietario()))
                .orElseGet(() -> {
                    User newOwner = resolveOwner(request.propietario());
                    ownerRepository.save(new Owner(newOwner, apartment, true));
                    return newOwner;
                });

        applyOwnerDetails(owner, request.propietario());

        return ApartmentResponse.from(apartment, PropietarioResponse.from(owner));
    }

    @Transactional
    public void deactivate(Long id) {
        Apartment apartment = apartmentRepository.findById(id)
                .orElseThrow(() -> new ApartmentNotFoundException(id));

        apartment.setActivo(false);
    }

    private User updatableOwnerOf(Long apartmentId, User currentOwner, PropietarioRequest request) {
        if (!currentOwner.getDocumentNumber().equals(trimmed(request.documentNumber()))) {
            throw new OwnerTransferNotSupportedException(apartmentId);
        }
        return currentOwner;
    }

    private User resolveOwner(PropietarioRequest request) {
        return residentUserService.resolveByDocument(detailsOf(request));
    }

    private void applyOwnerDetails(User user, PropietarioRequest request) {
        residentUserService.apply(user, detailsOf(request));
    }

    private static ResidentUserService.ResidentDetails detailsOf(PropietarioRequest request) {
        return ResidentUserService.ResidentDetails.normalized(
                request.firstName(),
                request.lastName(),
                request.documentNumber(),
                request.email(),
                request.phone()
        );
    }

    private PropietarioResponse principalOwnerResponseOf(Long apartmentId) {
        return ownerRepository.findByApartmentIdAndPrincipalTrue(apartmentId)
                .map(Owner::getUser)
                .map(PropietarioResponse::from)
                .orElse(null);
    }

    private Map<Long, PropietarioResponse> principalOwnersOf(List<Apartment> apartments) {
        if (apartments.isEmpty()) {
            return Map.of();
        }

        List<Long> apartmentIds = apartments.stream().map(Apartment::getId).toList();

        return ownerRepository.findPrincipalsWithUserByApartmentIdIn(apartmentIds).stream()
                .collect(Collectors.toMap(
                        owner -> owner.getApartment().getId(),
                        owner -> PropietarioResponse.from(owner.getUser()),
                        (first, duplicate) -> first
                ));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static String likePatternOf(String value) {
        return (value == null || value.isBlank()) ? null : "%" + value.trim().toLowerCase() + "%";
    }
}
