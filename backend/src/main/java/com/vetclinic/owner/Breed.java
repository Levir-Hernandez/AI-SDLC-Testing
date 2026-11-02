package com.vetclinic.owner;

public enum Breed {
    LABRADOR_RETRIEVER(Species.DOG), GERMAN_SHEPHERD(Species.DOG), GOLDEN_RETRIEVER(Species.DOG),
    BULLDOG(Species.DOG), DOG_MIXED(Species.DOG),
    SIAMESE(Species.CAT), PERSIAN(Species.CAT), MAINE_COON(Species.CAT), BENGAL(Species.CAT),
    CAT_MIXED(Species.CAT);

    private final Species species;

    Breed(Species species) { this.species = species; }

    public Species species() { return species; }

    public boolean belongsTo(Species s) { return species == s; }
}
