package com.targor.navfreeconverter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.XXHashFactory;

public class NavFreeConverter
{
    static ArrayList<String> groups = new ArrayList<>();

    public NavFreeConverter()
    {
        
    }
    public static void main(String[] args)
    {         
        
        /*args = new String[]{
            "-o","csv","-i","\"E:\\Nextcloud\\Projekte\\NavFreeConverter\\Favourites.xlm\""
        };*/
        
        // surpress useless slf4j messages in commandline
        System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "ERROR");
        System.setProperty("slf4j.internal.verbosity", "WARN");

        if (args.length == 0 || (args.length == 1 && args[0].toLowerCase().equals("/?")))
        {
            System.out.println("This tool can convert:");
            System.out.println("- Navfree favourites to csv.");
            System.out.println("- NavFree to Magic earth favourites (xlm).");
            System.out.println("- Magic earth (xlm )to csv.");
            System.out.println("");
            System.out.println("");
            System.out.println("Commandline: -o <outputtype -i <favouritex.XML | favouritex.XLM>");
            System.out.println("Output types: xlm (magic earth), csv (comman sperated value)");
            System.out.println("");
            System.out.println("Command examples:");
            System.out.println("java -jar -o xlm -i favourites.xml");
            System.out.println("java -jar -o csv -i favourites.xml");
            System.out.println("java -jar -o csv -i favourites.xlm");
            System.out.println("java -jar favourites.XLM");
        } else
        {
            String outputType = null;
            String inputFile = null;

            for (int i = 0; i < args.length; i++)
            {
                if (args[i].equals("-o") && args.length >= i + 2)
                {
                    outputType = args[i + 1];
                }

                if (args[i].equals("-i") && args.length >= i + 2)
                {
                    inputFile = args[i + 1];
                }
            }

            if (outputType == null || inputFile == null)
            {
                System.out.println("Output type or input file is not set.");
                return;
            }
            
            if (inputFile.startsWith("\"") && inputFile.endsWith("\""))
            {
               inputFile = inputFile.replace("\"", "");
            }
            
            File f = new File(inputFile);
            if (!f.getAbsolutePath().toLowerCase().endsWith("xml") && !f.getAbsolutePath().toLowerCase().endsWith("xlm")) 
            {
                System.out.println("inputfile seems to have the wrong file extension (allowed are xlm and xml)");
                return;
            }
            
            if (!f.exists())
            {
                System.out.println("File does not seem to exist, please provide a full path.");
                return;    
            }
            
            if (f.getAbsolutePath().toLowerCase().endsWith("xml"))
            {
                // navfree to magicearth
                if (outputType.equals("xlm"))
                {
                    File outputFile = new File("Favourites.xlm");
                    ArrayList<NavFreeFavourite> favs = GetNavFreeFavourites(f);
                    createMagicEarthDb(outputFile, favs);
                    writeCategories(outputFile, groups);
                    writeFavourites(outputFile, groups, favs);
                    System.out.println("Magic earth favourites have been saved to: Favourites.xlm");
                } 
                else if (outputType.equals("csv"))// navfree to csv
                {
                    File outputFile = new File("NavFree_Favourites.csv");
                    ArrayList<NavFreeFavourite> favs = GetNavFreeFavourites(f);
                    
                    Helper.writeToFile("Name#Group#Longitude#Latitude", outputFile.getAbsolutePath(), true);
                    for(int i=0;i<favs.size();i++)
                    {
                         
                        Helper.writeToFile( favs.get(i).name +"#" +
                                            favs.get(i).group+"#" +
                                            favs.get(i).longitude+"#" + 
                                            favs.get(i).latitude
                                , outputFile.getAbsolutePath(), true);
                    }
                    System.out.println("Magic earth favourites have been saved to: NavFree_Favourites.csv");
                }

            } // magic earth to csv
            else if (f.getAbsolutePath().toLowerCase().endsWith("xlm"))
            {
                convertMagicEarthToCsv(f);
                return;
            } 
        }
    }

    public static ArrayList<NavFreeFavourite> GetNavFreeFavourites(File f)
    {
        String data = Helper.readFile(f.getAbsolutePath());
        String[] splitted = data.split("\n");

        ArrayList<NavFreeFavourite> favs = new ArrayList<>();

        boolean hasGroup = false;
        String currentGroup = null;
        groups.clear();
        groups.add(" ");

        for (int i = 0; i < splitted.length; i++)
        {

            if (splitted[i].contains("<group"))
            {
                hasGroup = true;
                currentGroup = substrByNeedle(splitted[i], "name=\"", "\">");
                groups.add(currentGroup);
            }            if (splitted[i].contains("</group>"))
            {
                hasGroup = false;
                currentGroup = null;
            }

            if (splitted[i].contains("<item"))
            {
                NavFreeFavourite fav = new NavFreeFavourite();

                if (currentGroup != null)
                {
                    fav.group = currentGroup;
                }
                fav.name = substrByNeedle(splitted[i], "name=\"", "\"");
                fav.latitude = Long.parseLong(substrByNeedle(splitted[i], "lat=\"", "\""));
                fav.longitude = Long.parseLong(substrByNeedle(splitted[i], "lon=\"", "\""));
                favs.add(fav);
            }
        }

        return favs;
    }

    private static void convertMagicEarthToCsv(File f)
    {
        try{
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
            
            // first read groups
            ResultSet rs = connection.createStatement().executeQuery("select id,name from CTG order by id asc");
            ArrayList<String> groups = new ArrayList<>();
            while (rs.next()) {
                groups.add(rs.getString("name"));
            }
            
            rs = connection.createStatement().executeQuery("select name,desc,coord,categ from LMK");
            ArrayList<MagicEarthFavourite> favs = new ArrayList<>();
            while (rs.next()) 
            {
                MagicEarthFavourite fav = new MagicEarthFavourite();
                fav.name= rs.getString("name");
                fav.description= rs.getString("desc");
                
                String coord = String.valueOf(rs.getLong("coord"));
                fav.longitude = ConvertMagicEarthLongitudeToBase(coord);
                fav.latitude = ConvertMagicEarthLatitudeToBase(coord);
              
                
                int categ = rs.getInt("categ");
                for (int i=0;i<groups.size();i++)
                {
                    if ((i+1)+10000 == categ)
                    {
                        if (groups.get(i).equals("_#_parked_car_@_"))
                        {
                            fav.group="Parked car";
                        }
                        else if (groups.get(i).equals("_#_home_@_"))
                        {
                            fav.group="Home";
                        }
                        else
                        {
                            fav.group=groups.get(i);
                        }
                        break;
                    }
                }
                favs.add(fav);
            }
            
            // sort favourites first
            Collections.sort(favs, new CustomComparator());
            
            File outputFile = new File("MagicEarth_Favourites.csv");
            Helper.writeToFile("Name#Group#Description#Longitude#Latitude", outputFile.getAbsolutePath(), true);
            
            for (MagicEarthFavourite fav: favs)
            {
                Helper.writeToFile( fav.name +"#" +
                                    fav.group+"#" +
                                    fav.description+"#" +
                                    fav.longitude+"#" + 
                                    fav.latitude
                        , outputFile.getAbsolutePath(), true);
            }
            System.out.println("Magic earth favourites have been saved to: MagicEarth_Favourites.csv");
        }
        catch (SQLException e)
        {
            System.out.println("Could not open or process magicearth databse file.");
            e.printStackTrace(System.err);
        }
    }
    
    
    public static class CustomComparator implements Comparator<MagicEarthFavourite> {
        @Override
        public int compare(MagicEarthFavourite o1, MagicEarthFavourite o2) {            
            return o1.group.compareTo(o2.group);
        }
    }
    
    private static void writeFavourites(File saveTo, ArrayList<String> cats, ArrayList<NavFreeFavourite> favs)
    {
        try
        {
            // create a database connection
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + saveTo.getAbsolutePath());

            for (int i = 0; i < favs.size(); i++)
            {
                PreparedStatement pstmt = connection.prepareStatement("INSERT INTO LMK (id,tile,name,desc,coord,icon,categ,upd,body) values(?,?,?,?,?,?,?,?,?)");

                // id
                pstmt.setInt(1, getXXh3Hash(String.valueOf(new Date().getTime())));

                //tile
                pstmt.setInt(2, 170084);

                //name
                pstmt.setString(3, favs.get(i).name);

                //desc
                pstmt.setString(4, "");

                float longigute = convertMapFactorToBase(favs.get(i).longitude);
                float latitude = convertMapFactorToBase(favs.get(i).latitude);

                // coord
                pstmt.setLong(5, convertLongLatTo64BitMagicEarthValue(latitude, longigute));

                //icon
                pstmt.setInt(6, 6005);

                //categ
                int group = 10001; // default group will be located in root
                for (int a = 0; a < groups.size(); a++)
                {
                    if (groups.get(a).equals(favs.get(i).group))
                    {
                        group = 10000 + (a + 1);
                        break;
                    }
                }
                pstmt.setInt(7, group);

                //upd (upd is a update timestamp written in microseconds)
                pstmt.setLong(8, new Date().getTime() * 1000);

                pstmt.setBytes(9, new byte[]
                {
                });

                pstmt.execute();
            }
        } catch (SQLException e)
        {
            e.printStackTrace(System.err);
        }
    }

    private static String replaceUmlaut(String input)
    {

        // replace all lower Umlauts
        String output = input.replace("ü", "ue")
                .replace("ö", "oe")
                .replace("ä", "ae")
                .replace("ß", "ss");

        // first replace all capital Umlauts in a non-capitalized context (e.g. Übung)
        output = output.replaceAll("Ü(?=[a-zäöüß ])", "Ue")
                .replaceAll("Ö(?=[a-zäöüß ])", "Oe")
                .replaceAll("Ä(?=[a-zäöüß ])", "Ae");

        // now replace all the other capital Umlauts
        output = output.replace("Ü", "UE")
                .replace("Ö", "OE")
                .replace("Ä", "AE");

        return output;
    }

    public static void writeCategories(File saveTo, ArrayList<String> cats)
    {
        byte[] defaultCategory = new byte[]
        {
            (byte) 0x00, (byte) 0x75, (byte) 0x17, (byte) 0x00, (byte) 0x00
        };
        byte[] homeCategory = new byte[]
        {
            (byte) 0x00, (byte) 0xb3, (byte) 0x17, (byte) 0x00, (byte) 0x00
        };
        byte[] parkedCarCategory = new byte[]
        {
            (byte) 0x00, (byte) 0xe1, (byte) 0x17, (byte) 0x00, (byte) 0x00
        };

        try
        {
            // create a database connection
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + saveTo.getAbsolutePath());

            // set empty category
            PreparedStatement pstmtHome = connection.prepareStatement("INSERT INTO CTG (id,name,body) values(?,?,?)");

            for (int i = 0; i < cats.size(); i++)
            {
                PreparedStatement pstmt = connection.prepareStatement("INSERT INTO CTG (id,name,body) values(?,?,?)");
                pstmt.setInt(1, (i + 1));

                byte[] b = defaultCategory;

                if (i == cats.size() - 1)
                {
                    b = homeCategory;
                    pstmt.setString(2, "_#_home_@_");
                } else if (i == cats.size() - 2)
                {
                    b = parkedCarCategory;
                    pstmt.setString(2, "_#_parked_car_@_");
                } else
                {
                    pstmt.setString(2, replaceUmlaut(cats.get(i)));
                }

                //java.sql.Blob blob=new SerialBlob(b);
                pstmt.setBytes(3, b);
                pstmt.execute();
            }
        } catch (SQLException e)
        {
            e.printStackTrace(System.err);
        }
    }

    public static void createMagicEarthDb(File saveTo, ArrayList<NavFreeFavourite> favs)
    {

        try (
                // create a database connection
                Connection connection = DriverManager.getConnection("jdbc:sqlite:" + saveTo.getAbsolutePath()); Statement statement = connection.createStatement();)
        {
            statement.setQueryTimeout(120);  // set timeout to 30 sec.

            statement.executeUpdate("DROP TABLE IF EXISTS HEAD;");
            statement.executeUpdate("DROP TABLE IF EXISTS CTG;");
            statement.executeUpdate("DROP TABLE IF EXISTS LMK;");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS \"HEAD\" (\n"
                    + "	\"name\"	TEXT NOT NULL,\n"
                    + "	\"qt\"	INTEGER NOT NULL,\n"
                    + "	\"uid\"	INTEGER NOT NULL,\n"
                    + "	\"access\"	INTEGER NOT NULL,\n"
                    + "	\"valid\"	INTEGER NOT NULL,\n"
                    + "	\"icon\"	BIGINT NOT NULL DEFAULT 4294967295\n"
                    + ");");

            statement.executeUpdate("INSERT INTO HEAD (name, qt, uid, access, valid, icon) VALUES ('Favourites',5,-1,0,1,4294967295);");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS \"LMK\" (\n"
                    + "	\"id\"	INTEGER NOT NULL,\n"
                    + "	\"tile\"	INTEGER NOT NULL,\n"
                    + "	\"name\"	TEXT,\n"
                    + "	\"desc\"	TEXT,\n"
                    + "	\"coord\"	WGS_PT_INT NOT NULL,\n"
                    + "	\"icon\"	INTEGER,\n"
                    + "	\"categ\"	INTEGER,\n"
                    + "	\"upd\"	TIME_INT NOT NULL,\n"
                    + "	\"body\"	BLOB NOT NULL,\n"
                    + "	PRIMARY KEY(\"id\")\n"
                    + ");");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS \"CTG\" (\n"
                    + "	\"id\"	INTEGER NOT NULL,\n"
                    + "	\"name\"	TEXT NOT NULL UNIQUE,\n"
                    + "	\"body\"	BLOB NOT NULL,\n"
                    + "	PRIMARY KEY(\"id\")\n"
                    + ");");

            statement.executeUpdate("CREATE INDEX IF NOT EXISTS \"LMK_1\" ON \"LMK\" (\n"
                    + "	\"tile\"\n"
                    + ");");
        } catch (SQLException e)
        {
            e.printStackTrace(System.err);
        }
    }

    private static String substrByNeedle(String haystack, String start, String end)
    {
        int index1 = haystack.indexOf(start);
        int index2 = haystack.indexOf(end, index1 + start.length());
        return haystack.substring(index1 + start.length(), index2);
    }

    public static int getXXh3Hash(String text)
    {
        try
        {
            XXHashFactory factory = XXHashFactory.fastestInstance();

            byte[] data = text.getBytes("UTF-8");
            ByteArrayInputStream in = new ByteArrayInputStream(data);

            int seed = 0x9747b28c; // used to initialize the hash value, use whatever

            StreamingXXHash32 hash32 = factory.newStreamingHash32(seed);
            byte[] buf = new byte[8]; // for real-world usage, use a larger buffer, like 8192 bytes
            for (;;)
            {
                int read = in.read(buf);
                if (read == -1)
                {
                    break;
                }
                hash32.update(buf, 0, read);
            }
            return hash32.getValue();
        } catch (Exception exc)
        {
        }
        return -1;
    }

    /**
     * Converts a lat long value of (e.g. 51.0568F 13.7433F to
     * 701719316006899520)
     *
     * @param longitude
     * @param latitude
     * @return
     */
    static long convertLongLatTo64BitMagicEarthValue(float longitude, float latitude)
    {
        long result = ((long) (longitude * 3200000) << 32);
        result += ((long) (latitude * 3200000));
        return result;
    }

    /**
     * converts a mapfactor logn or lat value to a real long lat value (e.g.
     * 191581256 to 53.2170246)
     *
     * @param data
     * @return
     */
    static float convertMapFactorToBase(long data)
    {
        BigDecimal coord = new BigDecimal(data);
        return coord.divide(new BigDecimal(3600000L), 6, RoundingMode.HALF_UP).floatValue();
    }

    /**
     * reads magicearth longlat coordinate and extracts the longitude
     *
     * @param data
     * @return
     */
    static float ConvertMagicEarthLatitudeToBase(String data)
    {
        long l = new Long(data);
        l = (l << 32 >> 32);

        BigDecimal coord = new BigDecimal(l);
        return coord.divide(new BigDecimal("3200000"), 6, RoundingMode.HALF_UP).floatValue();
    }

    /**
     * reads magicearth longlat coordinate and extracts the latitude
     *
     * @param data
     * @return
     */
    static float ConvertMagicEarthLongitudeToBase(String data)
    {
        long l = new Long(data);
        l = l >>> 32;

        BigDecimal coord = new BigDecimal(l);
        return coord.divide(new BigDecimal("3200000"), 6, RoundingMode.HALF_UP).floatValue();
    }
}
