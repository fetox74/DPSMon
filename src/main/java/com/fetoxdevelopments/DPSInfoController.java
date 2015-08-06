package com.fetoxdevelopments;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class DPSInfoController
{
  private static final Logger LOG = LogManager.getLogger(DPSInfoController.class.getName());
  private static final int SECS_TO_INTEGRATE_LONG = 10;
  private static final int SECS_TO_INTEGRATE_SHORT = 3;

  private DPSInfo dpsInfo = new DPSInfo();
  private Timestamp recentmostLogEntryTimestamp = null;
  private long corrValue = 0;

  @RequestMapping("/")
  public DPSInfo dpsInfo()
  {
    return dpsInfo;
  }

  @Scheduled(fixedDelay = 250)
  public void updateFunction()
    throws IOException
  {
    File currentEveLog = getLastModifiedFile(System.getProperty("user.home") + "\\Documents\\EVE\\logs\\Gamelogs");

    dpsInfo.dpsOutgoingLong = 0;
    dpsInfo.dpsIncomingLong = 0;
    dpsInfo.dpsOutgoingShort = 0;
    dpsInfo.dpsIncomingShort = 0;
    dpsInfo.secsToIntegrateLong = SECS_TO_INTEGRATE_LONG;
    dpsInfo.secsToIntegrateShort = SECS_TO_INTEGRATE_SHORT;
    integrateDPS(currentEveLog);

    dpsInfo.eveLogCurrent = currentEveLog.toString();
  }

  private File getLastModifiedFile(String dirPath)
  {
    File dir = new File(dirPath);
    File[] files = dir.listFiles();
    if(files == null || files.length == 0)
    {
      return null;
    }

    File lastModifiedFile = files[0];
    for(int i = 1; i < files.length; i++)
    {
      if(lastModifiedFile.lastModified() < files[i].lastModified())
      {
        lastModifiedFile = files[i];
      }
    }
    return lastModifiedFile;
  }

  private void integrateDPS(File eveLog)
    throws IOException
  {
    BufferedReader bufferedReader = new BufferedReader(new FileReader(eveLog));
    Pattern hpPattern = Pattern.compile("<color=(0xff00ffff|0xffcc0000)><b>(.*?)</b>");
    Calendar calendar = Calendar.getInstance();
    List<String> logEntrys = new ArrayList<String>();
    String logEntry;

    do
    {
      logEntry = bufferedReader.readLine();
      logEntrys.add(logEntry);
    }
    while(logEntry != null);

    long currentTimeMillis = System.currentTimeMillis();
    currentTimeMillis += corrValue;

    calendar.setTimeInMillis(currentTimeMillis);
    Timestamp currentTime = new Timestamp(calendar.getTime().getTime());

    calendar.setTimeInMillis(currentTimeMillis);
    calendar.add(Calendar.SECOND, -SECS_TO_INTEGRATE_LONG);
    Timestamp barrierLong = new Timestamp(calendar.getTime().getTime());

    calendar.setTimeInMillis(currentTimeMillis);
    calendar.add(Calendar.SECOND, -SECS_TO_INTEGRATE_SHORT);
    Timestamp barrierShort = new Timestamp(calendar.getTime().getTime());

    LOG.debug("---");
    LOG.debug("Current time: " + currentTime.toString());
    LOG.debug("Barrier Long: " + barrierShort.toString());
    LOG.debug("Barrier Short: " + barrierLong.toString());
    LOG.debug("Correction Value: " + corrValue);

    for(int i = logEntrys.size() - 1; i >= 0; i--)
    {
      logEntry = logEntrys.get(i);
      if(logEntry != null && logEntry.startsWith("["))
      {
        Timestamp logEntryTimestamp = Timestamp.valueOf(logEntry.substring(2, 21).replace(".", "-"));
        if(recentmostLogEntryTimestamp == null)
        {
          recentmostLogEntryTimestamp = logEntryTimestamp;
        }
        else
        {
          if(logEntryTimestamp.after(recentmostLogEntryTimestamp))
          {
            long adjCorrValue = logEntryTimestamp.getTime() - currentTime.getTime();
            LOG.debug("Adjusted correction Value: " + adjCorrValue);
            if(adjCorrValue < 0 && corrValue + adjCorrValue < corrValue || adjCorrValue > 0)
            {
              corrValue = corrValue + (adjCorrValue / 5);
              LOG.debug("Adjusting correction value to: " + corrValue);
            }
            recentmostLogEntryTimestamp = logEntryTimestamp;
          }
        }

        if(logEntryTimestamp.after(barrierLong) && logEntry.contains("(combat)"))
        {
          Matcher matcher = hpPattern.matcher(logEntry);
          while(matcher.find())
          {
            if(logEntry.contains("<font size=10>to</font>"))
            {
              dpsInfo.dpsOutgoingLong += Integer.parseInt(matcher.group(2));
              LOG.trace("Added " + logEntry + " to dpsOutgoingLong..");
            }
            if(logEntry.contains("<font size=10>from</font>"))
            {
              dpsInfo.dpsIncomingLong += Integer.parseInt(matcher.group(2));
              LOG.trace("Added " + logEntry + " to dpsIncomingLong..");
            }
            if(logEntry.contains("<font size=10>to</font>") && logEntryTimestamp.after(barrierShort))
            {
              dpsInfo.dpsOutgoingShort += Integer.parseInt(matcher.group(2));
              LOG.trace("Added " + logEntry + " to dpsOutgoingShort..");
            }
            if(logEntry.contains("<font size=10>from</font>") && logEntryTimestamp.after(barrierShort))
            {
              dpsInfo.dpsIncomingShort += Integer.parseInt(matcher.group(2));
              LOG.trace("Added " + logEntry + " to dpsIncomingShort..");
            }
          }
        }
      }
    }

    dpsInfo.dpsOutgoingLong /= (float) SECS_TO_INTEGRATE_LONG;
    dpsInfo.dpsIncomingLong /= (float) SECS_TO_INTEGRATE_LONG;
    dpsInfo.dpsOutgoingShort /= (float) SECS_TO_INTEGRATE_SHORT;
    dpsInfo.dpsIncomingShort /= (float) SECS_TO_INTEGRATE_SHORT;
  }
}