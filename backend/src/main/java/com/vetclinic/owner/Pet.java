package com.vetclinic.owner;

import com.vetclinic.shared.error.BadRequestException;
import jakarta.persistence.*;

@Entity
@Table(name = "pet", indexes = @Index(name = "idx_pet_owner", columnList = "owner_id"))
public class Pet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Species species;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Breed breed;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    protected Pet() {}

    public Pet(String name, Species species, Breed breed, Owner owner) {
        if (!breed.belongsTo(species)) {
            throw new BadRequestException("BREED_SPECIES_MISMATCH",
                    "Breed " + breed + " is not a " + species + " breed");
        }
        this.name = name;
        this.species = species;
        this.breed = breed;
        this.owner = owner;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Species getSpecies() { return species; }
    public Breed getBreed() { return breed; }
    public Owner getOwner() { return owner; }
}
