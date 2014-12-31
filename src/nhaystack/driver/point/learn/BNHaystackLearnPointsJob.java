//
// Copyright (c) 2012, J2 Innovations
// Licensed under the Academic Free License version 3.0
//
// History:
//   14 Apr 2014  Mike Jarmy  Creation

package nhaystack.driver.point.learn;

import java.util.*;

import javax.baja.job.*;
import javax.baja.naming.*;
import javax.baja.sys.*;
import javax.baja.util.*;

import org.projecthaystack.*;
import org.projecthaystack.client.*;

import nhaystack.*;
import nhaystack.driver.*;
import nhaystack.res.*;

/**
  * BNHaystackLearnPointsJob is a Job which 'learns' all the remote
  * points from a remote haystack server.
  */
public class BNHaystackLearnPointsJob extends BSimpleJob 
{
    /*-
    class BNHaystackLearnPointsJob
    {
        properties
        {
        }
    }
    -*/
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $nhaystack.driver.point.BNHaystackLearnPointsJob(677323658)1.0$ @*/
/* Generated Mon Apr 07 08:34:06 EDT 2014 by Slot-o-Matic 2000 (c) Tridium, Inc. 2000 */

////////////////////////////////////////////////////////////////
// Type
////////////////////////////////////////////////////////////////
  
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BNHaystackLearnPointsJob.class);

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

    public BNHaystackLearnPointsJob() {}

    public BNHaystackLearnPointsJob(BNHaystackServer server)
    {
        this.server = server;
    }

    public void doCancel(Context ctx) 
    {
        super.doCancel(ctx);
        throw new JobCancelException();
    }

    public void run(Context ctx) throws Exception 
    {
        NameGenerator nameGen = new NameGenerator();
        Map entries = new TreeMap();
        
        HClient client = server.getHaystackClient();
        HGrid grid = client.readAll("point");
        for (int i = 0; i < grid.numRows(); i++)
        {
            HRow row = grid.row(i);

            String kind = row.getStr("kind");
            String name = SlotPath.escape(nameGen.makeUniqueName(row.dis()));

            BNHaystackPointEntry entry = new BNHaystackPointEntry();

            if      (kind.equals("Bool"))   entry.setFacets(makeBoolFacets(row));
            else if (kind.equals("Number")) entry.setFacets(makeNumberFacets(row));
            else if (kind.equals("Str"))    
            {
                BFacets facets = makeStrFacets(row);
                if (facets != null) entry.setFacets(facets);
            }

            entry.setId(BHRef.make(row.id()));
            entry.setImportedTags(BHTags.make(row));

            entries.put(name, entry);
        }

        Iterator it = entries.keySet().iterator();
        while (it.hasNext())
        {
            String name = (String) it.next();
            BNHaystackPointEntry entry = (BNHaystackPointEntry) entries.get(name);
            add(name, entry);
        }
    }

    private BFacets makeBoolFacets(HDict rec)
    {
        if (!rec.has("enum")) return BFacets.NULL;

        String[] tokens = TextUtil.split(rec.getStr("enum"), ',');

        // first true, then false
        return BFacets.makeBoolean(tokens[1], tokens[0]); 
    }

    private BFacets makeNumberFacets(HDict rec)
    {
        if (!rec.has("unit")) return BFacets.NULL;

        String unit = rec.getStr("unit");
        if (unit.toLowerCase().equals("none"))
            return BFacets.NULL;

        return BFacets.make(
            BFacets.UNITS,
            Resources.toBajaUnit(
                Resources.getSymbolUnit(unit)));
    }

    private BFacets makeStrFacets(HDict rec)
    {
        if (!rec.has("enum")) return BFacets.NULL;

        String[] tokens = TextUtil.split(rec.getStr("enum"), ',');

        for (int i = 0; i < tokens.length; i++)
            if (!SlotPath.isValidName(tokens[i])) return null;

        return BFacets.makeEnum(BEnumRange.make(tokens));
    }

////////////////////////////////////////////////////////////////
// Attributes
////////////////////////////////////////////////////////////////

    private BNHaystackServer server = null;
}
