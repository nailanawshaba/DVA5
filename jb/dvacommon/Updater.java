package jb.dvacommon;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import jb.common.FileUtilities;
import jb.common.VersionComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.innahema.collections.query.queriables.Queryable;

public class Updater
{
    public static ArrayList<BaseUpdater> AllUpdaters;
    final static Logger logger = LoggerFactory.getLogger(Updater.class);
    private static File jarFolder;
    
    static
    {
        jarFolder = FileUtilities.getJarFolder(Updater.class);
        try {
            AllUpdaters = new ArrayList<>();
            if (jarFolder != null && jarFolder.getPath().toLowerCase().startsWith("/Users/jb/Software/DVA/build/Debug".toLowerCase())) {
                logger.info("Using the local drop source");
                AllUpdaters.add(new SimpleWebsiteUpdater(new URL("file:///Users/jb/Software/DVA/build/TestUpdateDrop/")));
            }
            AllUpdaters.add(new WAzureUpdater(new URL("http://dvaupdate.blob.core.windows.net/")));
            //AllUpdaters.add(new SimpleWebsiteUpdater(new URL("http://jonathanboles.com/dva/")));
        } catch (MalformedURLException ignored) {}
    }
    
    public static BaseUpdater updateAvailable(String currentVersion, String suppressedVersion)
    {
        logger.info("Checking available updaters for a version newer than {}, suppressed version {}", currentVersion, suppressedVersion);
        logger.info("Jar folder: {}", jarFolder != null ? jarFolder.getPath() : "<null>");

        BaseUpdater latestUpdater = Queryable.from(AllUpdaters)
            .map(u -> {
                String uv = u.getLatestVersion(); 
                logger.debug("Checked {}: latest is {}", u.getClass().getName(), uv);
                return u;
            })
            .filter(u -> u.getLatestVersion() != null)
            .filter(u -> suppressedVersion == null || VersionComparator.Instance.compare(u.getLatestVersion(), suppressedVersion) > 0)
            .filter(u -> VersionComparator.Instance.compare(u.getLatestVersion(), currentVersion) > 0)
            .max((u1, u2) -> VersionComparator.Instance.compare(u1.getLatestVersion(), u2.getLatestVersion()));
         
        if (latestUpdater != null)
            logger.info("Newer version found using updater {}", latestUpdater.getClass().getName());
        else
            logger.info("No new version");
        return latestUpdater;
    }
}
