package com.example.th9.model;
/** Public account metadata; password material never leaves the data layer. */
public final class Account {
    public final String id,username,fullName,role;
    public final boolean enabled;
    public Account(String id,String username,String fullName,String role,boolean enabled){this.id=id;this.username=username;this.fullName=fullName;this.role=role;this.enabled=enabled;}
    public boolean isAdmin(){return "ADMIN".equals(role);}
}
