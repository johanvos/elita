
package com.gluonhq.wave;

/**
 *
 * @author johan
 */
public class Contact {
    
    private String name;
    private String uuid;
    private String nr;
    private String avatarPath;
    
    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    public String getNr() {
        return nr;
    }
    
    public String getAvatarPath() {
        return avatarPath;
    }
    
    public Contact(String name, String uuid, String nr) {
        this.name = name;
        this.uuid = uuid;
        this.nr = nr;
    }
    
    public void setAvatarPath(String b) {
        this.avatarPath = b;
    }
    
}
