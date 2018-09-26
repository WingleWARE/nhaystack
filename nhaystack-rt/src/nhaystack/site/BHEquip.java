//
// Copyright (c) 2012, J2 Innovations
// Licensed under the Academic Free License version 3.0
//
// History:
//   06 Feb 2013  Mike Jarmy       Creation
//   08 May 2018  Eric Anderson    Migrated to slot annotations, added missing @Overrides annotations
//   26 Sep 2018  Andrew Saunders  Added shared constants
//

package nhaystack.site;

import javax.baja.nre.annotations.NiagaraProperty;
import javax.baja.nre.annotations.NiagaraType;
import javax.baja.sys.BComponent;
import javax.baja.sys.BIcon;
import javax.baja.sys.Property;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import nhaystack.BHDict;
import nhaystack.util.NHaystackConst;
import nhaystack.server.NHServer;
import nhaystack.server.Nav;
import nhaystack.server.TagManager;
import org.projecthaystack.HDict;
import org.projecthaystack.HDictBuilder;
import org.projecthaystack.HRef;
import org.projecthaystack.HStr;

/**
 *  BHEquip represents a Haystack 'equip' rec.
 */
@NiagaraType
@NiagaraProperty(
  name = "haystack",
  type = "BHDict",
  defaultValue = "BHDict.make(\"navNameFormat:\\\"%parent.displayName%\\\"\")",
  override = true
)
public class BHEquip extends BHTagged implements NHaystackConst
{
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $nhaystack.site.BHEquip(2564166230)1.0$ @*/
/* Generated Sun Nov 19 22:45:42 EST 2017 by Slot-o-Matic (c) Tridium, Inc. 2012 */

////////////////////////////////////////////////////////////////
// Property "haystack"
////////////////////////////////////////////////////////////////
  
  /**
   * Slot for the {@code haystack} property.
   * @see #getHaystack
   * @see #setHaystack
   */
  public static final Property haystack = newProperty(0, BHDict.make("navNameFormat:\"%parent.displayName%\""), null);

////////////////////////////////////////////////////////////////
// Type
////////////////////////////////////////////////////////////////
  
  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BHEquip.class);

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

    /**
      * Return default values for those tags which are essential for
      * defining this component.
      */
    @Override
    public HDict getDefaultEssentials()
    {
        return ESSENTIALS;
    }
  
    /**
      * Generate all the tags for this component, including
      * autogenerated tags like "id", and any other tags 
      * defined in the 'haystack' property.
      */
    @Override
    public HDict generateTags(NHServer server)
    {
        HDictBuilder hdb = new HDictBuilder();
        hdb.add(server.getTagManager().generateComponentTags(this));
        hdb.add(TagManager.convertRelationsToRefTags(server.getTagManager(), this));

        // add annotated
        HDict tags = getHaystack().getDict();
        hdb.add(server.getTagManager().convertAnnotatedRefTags(tags));

        // navName
        String navName = Nav.makeNavName(this, tags);
        hdb.add("navName", navName);

        // dis
        String dis = createDis(server, tags, navName);
        hdb.add("dis", dis);

        // add id
        HRef ref = server.getTagManager().makeComponentRef(this).getHRef();
        hdb.add("id", HRef.make(ref.val, dis));

        // add equip
        hdb.add("equip");

        // add misc other tags
        hdb.add("axType", getType().toString());
        hdb.add("axSlotPath", getSlotPath().toString());

        return hdb.toDict();
    }

    private static String createDis(NHServer server, HDict tags, String navName)
    {
        String dis = navName;

        // site
        if (tags.has(SITE_REF))
        {
            BComponent site = server.getTagManager().lookupComponent(tags.getRef(SITE_REF));
            if (site != null)
            {
                HDict siteTags = BHDict.findTagAnnotation(site);
                String siteNavName = Nav.makeNavName(site, siteTags);

                dis = siteNavName + ' ' + navName;
            }
        }

        return dis;
    }

    @Override
    public BIcon getIcon() { return ICON; }

////////////////////////////////////////////////////////////////
// Attributes
////////////////////////////////////////////////////////////////

    public static final BIcon ICON = BIcon.make("module://nhaystack/nhaystack/icons/equip.png");

    private static final HDict ESSENTIALS;
    static
    {
        HDictBuilder hd = new HDictBuilder();
        hd.add(SITE_REF, HRef.make("null"));
        hd.add("floorName", HStr.make(""));
        ESSENTIALS = hd.toDict();
    }
}

