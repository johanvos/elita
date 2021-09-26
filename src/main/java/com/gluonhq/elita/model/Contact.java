
package com.gluonhq.elita.model;

/**
 *
 * @author johan
 */
public class Contact {
    
    private String name;
    private String uuid;
    private String nr;
    
    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    public String getNr() {
        return nr;
    }
    
    public Contact(String name, String uuid, String nr) {
        this.name = name;
        this.uuid = uuid;
        this.nr = nr;
    }
    
}
