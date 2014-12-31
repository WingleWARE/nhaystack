//
// Copyright (c) 2012, J2 Innovations
// Licensed under the Academic Free License version 3.0
//
// History:
//   07 Nov 2011  Richard McElhinney  Creation
//   28 Sep 2012  Mike Jarmy          Ported from axhaystack
//
package nhaystack.server;

import java.util.*;
import javax.baja.control.*;
import javax.baja.history.*;
import javax.baja.history.ext.*;
import javax.baja.log.*;
import javax.baja.naming.*;
import javax.baja.schedule.*;
import javax.baja.status.*;
import javax.baja.sys.*;
import javax.baja.timezone.*;
import javax.baja.units.*;
import javax.baja.util.*;
import com.tridium.util.backport.concurrent.ConcurrentHashMap;

import org.projecthaystack.*;
import org.projecthaystack.io.*;
import org.projecthaystack.util.*;

import nhaystack.*;
import nhaystack.res.*;
import nhaystack.site.*;
import nhaystack.util.*;

/**
  * ScheduleManager does various task associated with generating tags
  * and looking things up based on ids, etc.
  */
class ScheduleManager
{
    ScheduleManager(
        NHServer server,
        BNHaystackService service)
    {
        this.server = server;
        this.service = service;
    }

    /**
      * this gets called during 'rebuildCache()'
      */
    void makePointEvents(BComponent[] points)
    {
        for (int i = 0; i < points.length; i++)
        {
            HRef id = server.getTagManager().makeComponentRef(points[i]).getHRef();

            // cancel any existing ticket
            Clock.Ticket ticket = (Clock.Ticket) ticketById.remove(id);
            if (ticket != null) 
            {
                ticket.cancel();
            }

            // make a new ticket
            makeTicketFromItems(id, hisItems(points[i]));
        }
    }

    /**
      * Apply the given schedule to its parent point, by calling onPointWrite
      */
    void applySchedule(BHScheduleEvent event)
    {
//        HRef id = event.getId().getRef();
//
//        BComponent point = (BComponent) server.getTagManager().lookupComponent(id);
//        HDict tags = BHDict.findTagAnnotation(point);
//
////System.out.println("ScheduleManager.applySchedule: " + 
////    event.getId() + ", " + event.getValue() + ", " + 
////    point.getSlotPath());
//
//        // set the point
//        HDictBuilder hdb = new HDictBuilder();
//        hdb.add("id", id);
//        HDict rec = hdb.toDict();
//        int level = tags.getInt("schedulable");
//        HVal val = TypeUtil.fromBajaSimple((BSimple) event.getValue());
//        server.onPointWrite(rec, level, val, "", null);
//
//        // remove existing ticket
//        ticketById.remove(id);
//
//        // try to make another ticket
//        makeTicketFromItems(id, hisItems(point));
    }

    protected void onScheduleWrite(HDict rec, HHisItem[] items)
    {
        ////////////////////////////////////////////////////////////////
        for (int i = 0; i < items.length; i++)
            System.out.println(i + ", " + items[i]);
        ////////////////////////////////////////////////////////////////

        BComponent comp = server.getTagManager().lookupComponent(rec.id());
        if (comp == null) 
            throw new BajaRuntimeException("Cannot find component for " + rec.id());

        if (comp instanceof BControlPoint)
            writePointSchedule((BControlPoint) comp, items);
        else if (comp instanceof BWeeklySchedule)
            writeWeeklySchedule((BWeeklySchedule) comp, items);
        else
            throw new BajaRuntimeException("Cannot write schedule to " + comp.getSlotPath());
    }

////////////////////////////////////////////////////////////////
// private
////////////////////////////////////////////////////////////////

    private void writePointSchedule(BControlPoint point, HHisItem[] items)
    {
        HDict orig = BHDict.findTagAnnotation(point);
        if (orig == null) orig = HDict.EMPTY;

        HDictBuilder hdb = new HDictBuilder();
        Iterator itr = orig.iterator();
        while (itr.hasNext())
        {
            Map.Entry e = (Map.Entry) itr.next();
            String name = (String) e.getKey();
            HVal val = (HVal) e.getValue();

            if (name.equals("weeklySchedule")) continue;
            if (name.equals("tz") && items.length > 0) continue;
            hdb.add(name, val);
        }

        HGrid schedule = HGridBuilder.hisItemsToGrid(HDict.EMPTY, items);
        hdb.add("weeklySchedule", HZincWriter.gridToString(schedule));
        if (items.length > 0)
            hdb.add("tz", items[0].ts.tz.toString());

        if (point.get("haystack") == null)
            point.add("haystack", BHDict.make(hdb.toDict()));
        else
            point.set("haystack", BHDict.make(hdb.toDict()));
    } 

    private void writeWeeklySchedule(BWeeklySchedule sched, HHisItem[] items)
    {
        items = ScheduleManager.normalizeWeek(items);
        BWeekSchedule week = new BWeekSchedule();

        for (int i = 0; i < items.length; i++)
        {
            BSimple value = TypeUtil.toBajaSimple(items[i].val);

            // skip values that correspond to the schedule's default output
            if (value.equals(sched.getDefaultOutput())) continue;
            BStatusValue sv = makeStatusValue(value, BStatus.ok);

            // start
            BTime start = BTime.make(
                BAbsTime.make(
                    items[i].ts.millis(), 
                    TypeUtil.toBajaTimeZone(items[i].ts.tz)));

            // finish (just before midnight if its the last one)
            BTime finish = null;
            if (i < items.length-1)
            {
                finish = BTime.make(
                    BAbsTime.make(
                        items[i+1].ts.millis(), 
                        TypeUtil.toBajaTimeZone(items[i+1].ts.tz)));
            }
            else
            {
                finish = BTime.make(23, 59, 59, 999);
            }

            // apply to the appropriate weekday
            switch (items[i].ts.date.weekday())
            {
                case 1: week.getSunday()    .add(start, finish, sv, null); break;
                case 2: week.getMonday()    .add(start, finish, sv, null); break;
                case 3: week.getTuesday()   .add(start, finish, sv, null); break;
                case 4: week.getWednesday() .add(start, finish, sv, null); break;
                case 5: week.getThursday()  .add(start, finish, sv, null); break;
                case 6: week.getFriday()    .add(start, finish, sv, null); break;
                case 7: week.getSaturday()  .add(start, finish, sv, null); break;

                default: 
                    throw new IllegalStateException();
            }
        }

        sched.getSchedule().set("week", week);
    }

    /**
      * Make sure all of the items are scheduled to happen this week.  
      */
    private static HHisItem[] normalizeWeek(HHisItem[] items)
    {
        HTimeZone tz = items[0].ts.tz;
        long curMillis = System.currentTimeMillis();

        // compute sunday of this week
        HDateTime now = HDateTime.make(curMillis, tz);
        HDateTime sunday = HDateTime.make(
            now.date.minusDays(now.date.weekday()-1), 
            HTime.MIDNIGHT, tz);

        return doNormalizeWeek(items, sunday);
    }

    private static BStatusValue makeStatusValue(BValue value, BStatus status)
    {
        if (value instanceof BIBoolean) 
            return new BStatusBoolean(((BIBoolean)value).getBoolean(), status);

        if (value instanceof BINumeric) 
            return new BStatusNumeric(((BINumeric)value).getNumeric(), status);

        if (value instanceof BIEnum) 
            return new BStatusEnum(((BIEnum)value).getEnum(), status);

        return new BStatusString(value.toString(), status);
    }

    /**
      * read the HHisItems from the 'weeklySchedule' tag
      */
    private static HHisItem[] hisItems(BComponent point)
    {
        HDict tags = BHDict.findTagAnnotation(point);
        HGrid grid = (new HZincReader(tags.getStr("weeklySchedule"))).readGrid();
        return HHisItem.gridToItems(grid);
    }

    /**
      * create a ticket for the next future HHisItem
      */
    private void makeTicketFromItems(HRef id, HHisItem[] items)
    {
        if (items.length == 0) return;

        HTimeZone tz = items[0].ts.tz;
        long curMillis = System.currentTimeMillis();

        // compute sunday of this week
        HDateTime now = HDateTime.make(curMillis, tz);
        HDateTime sunday = HDateTime.make(
            now.date.minusDays(now.date.weekday()-1), 
            HTime.MIDNIGHT, tz);

        // try this week
        if (!scheduleWeeklyTicket(id, doNormalizeWeek(items, sunday), curMillis))
        {
            // maybe next week
            sunday = HDateTime.make(sunday.date.plusDays(7), HTime.MIDNIGHT, tz);
            scheduleWeeklyTicket(id, doNormalizeWeek(items, sunday), curMillis);
        }
    }

    /**
      * Attempt to schedule something for this week from the given HHisItems.
      * If there is nothing available, then  
      */
    private boolean scheduleWeeklyTicket(HRef id, HHisItem[] items, long curMillis)
    {
        BTimeZone tz = TypeUtil.toBajaTimeZone(items[0].ts.tz);
        BAbsTime now = BAbsTime.make(curMillis, tz);

        // try to find an item from the future
        for (int i = 0; i < items.length; i++)
        {
            HHisItem item = items[i];
            if (item.ts.millis() > now.getMillis())
            {
                BAbsTime absTime = BAbsTime.make(
                    item.ts.millis(), 
                    TypeUtil.toBajaTimeZone(item.ts.tz));

                if (LOG.isTraceOn())
                    LOG.trace("Scheduling a ticket at " + item.ts + " for " + id);
                
                ticketById.put(
                    id, 
                    Clock.schedule(
                        service, 
                        absTime,
                        BNHaystackService.applySchedule,
                        new BHScheduleEvent(
                            BHRef.make(id),
                            TypeUtil.toBajaSimple(item.val))));

                return true;
            }
        }

        return false;
    }

    /**
      * Make sure all of the items are scheduled to happen this week.  
      */
    private static HHisItem[] doNormalizeWeek(HHisItem[] items, HDateTime thisSun)
    {
        if (items.length == 0) throw new IllegalStateException();

        HTimeZone tz = items[0].ts.tz;
        HHisItem[] future = new HHisItem[items.length];

        // compute sunday of next week
        HDateTime nextSun = HDateTime.make(
            thisSun.date.plusDays(7), 
            HTime.MIDNIGHT, tz);

        for (int i = 0; i < items.length; i++)
        {
            HDateTime ts = items[i].ts;

            // subtract weeks until we are before next sunday
            while (ts.millis() >= nextSun.millis())
                ts = HDateTime.make(ts.date.minusDays(7), ts.time, tz);

            // add weeks until we are after this sunday
            while (ts.millis() < thisSun.millis())
                ts = HDateTime.make(ts.date.plusDays(7), ts.time, tz);

            future[i] = HHisItem.make(ts, items[i].val);
        }

        Arrays.sort(
            future,
            new Comparator() {
                public int compare(Object o1, Object o2) {
                    HHisItem h1 = (HHisItem) o1;
                    HHisItem h2 = (HHisItem) o2;
                    return (int) (h1.ts.millis() - h2.ts.millis());
                }
            });

        return future;
    }

////////////////////////////////////////////////////////////////
// attribs 
////////////////////////////////////////////////////////////////

    private static final Log LOG = Log.getLog("nhaystack");

    private final NHServer server;
    private final BNHaystackService service;

    private final ConcurrentHashMap ticketById = new ConcurrentHashMap(); // <HRef,Clock.Ticket>
}

