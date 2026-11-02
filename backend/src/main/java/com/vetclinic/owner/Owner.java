package com.vetclinic.owner;

import jakarta.persistence.*;

@Entity
@Table(name = "owner", indexes = @Index(name = "idx_owner_phone", columnList = "phone", unique = true))
public class Owner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String phone;

    protected Owner() {}

    public Owner(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
}
