package utility;



import com.sun.org.apache.bcel.internal.generic.RETURN;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.Date;

public class MysqlHelper {
    private Workbook workbook;
    private InputStream inputStream;

    /**
     * 从类excel文件中读取数据的方法
     * @param xlsFilePath 要读取的类excel文件的文件路径
     * @return 返回读到的数据的集合，一行对应数组的一个元素
     */
    private String[] getFileDataFromExcel(String xlsFilePath){
        try {
            String data[] = new String[5000];
            // 创建输入流，读取Excel
            File file = new File(xlsFilePath);
            inputStream = new FileInputStream(file.getAbsolutePath());
            // jxl提供的Workbook类
            workbook = Workbook.getWorkbook(inputStream);
            // Excel的页签数量
            int sheet_size = workbook.getNumberOfSheets();
//            System.out.println(sheet_size);
            String cellinfo = null;
            String output = "";
            for (int index = 0; index < sheet_size; index++) {
                // 每个页签创建一个Sheet对象
                Sheet sheet = workbook.getSheet(index);
                // sheet.getRows()返回该页的总行数
                for (int i = 0; i < sheet.getRows(); i++) {
                    // sheet.getColumns()返回该页的总列数
                    for (int j = 0; j < sheet.getColumns(); j++) {
                        cellinfo = sheet.getCell(j, i).getContents();
                        if (!cellinfo.equals("")){
                            output += (cellinfo + " ");
                        }
                    }
                    data[i] = output;
                    output = "";
                }
            }
            return data;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (BiffException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 从txt文件中读取数据的方法
     * @param path 要读取的txt文件的文件路径
     * @return 返回读到的数据的集合，一行对应数组的一个元素
     */
    private String[] getFileDataFromTxt(String path){
        String data[] = new String[100];
        File file = new File(path);
        try {
            LineIterator lineIterator = FileUtils.lineIterator(file);
            int i = 0;
            while(lineIterator.hasNext()){
                String line = lineIterator.nextLine();
                data[i] = line;
                i++;
            }
            return data;
        } catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 小规模数据插入数据库的方法
     * @param fileType 要读取的文件类型 0为excel，1为txt
     * @param filePath 要读取文件的文件路径
     * @param tableName 要插入到数据库的哪个表对应的表名
     * @param tableColumn 要在哪些列插入数据，这些列对应的列名的集合
     * @param splitOperator 文件中每行数据不同字段的分隔符
     * @return 返回插入数据sql命令对应的字符串
     */
    private int saveData2DB(int fileType, String filePath, String tableName, String[] tableColumn, String splitOperator){
        //成功返回1，失败返回0
//        "/Users/xiezhenyu/Desktop/课件/数据库开发技术/assignments/2017/2/分配方案.xls"
        String data[] = null;
        switch (fileType){
            case 0: //excel
                data = getFileDataFromExcel(filePath);
                break;
            case 1://txt
                data = getFileDataFromTxt(filePath);
                break;
        }
        data = removeArrayEmptyTextBackNewArray(data);

        //更加抽象地实现
        String sql = "INSERT INTO `" + tableName + "` (";
        for(int i = 0;i < tableColumn.length;i++){
            if(i != tableColumn.length-1){
                sql += "`" + tableColumn[i] + "`,";
            }
            else{
                sql += "`" + tableColumn[i] + "`";
            }
        }
        sql += ") VALUES (";
        String[] cell;
        for (int i = 0; i < data.length;i++){
            if(i == 0){continue;}
            cell = data[i].split(splitOperator);
            for (int j = 0; j < cell.length;j++){
                if(j != cell.length - 1){
                    sql += ("'"+cell[j]+"'" + ",");
                }else if(j == cell.length - 1 && i != data.length - 1){
                    sql += ("'"+cell[j]+"'" + "),(");
                }else{
                    sql += ("'"+cell[j]+"'" + ");");

                }
            }
        }
        int result = executeSql(sql);
        return result;
    }

    /**
     * 完成创建表的操作
     * @param tableName 要创建的表的名称
     * @param columns 要创建表的列名的数组
     * @param isIDNeeded 是否需要指明特定列为主键，为true时，后一个参数primary_key_set才有意义；为false，默认传入列名中第一列为主键
     * @param primary_key_set 需要特殊指明的主键列的集合
     * @return 返回创建表命令对应的sql命令的String字符串
     */
    private String getCreateTableSQL(String tableName, String[] columns, boolean isIDNeeded, String[] primary_key_set){
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS " + "`" + tableName + "`(");
        String singleAttr;
        if(isIDNeeded){
            for (int i = 0; i < columns.length;i++){
                singleAttr = "`" + columns[i] + "` VARCHAR(255) NOT NULL,";
                sql.append(singleAttr);
            }
            String primary_key = "";
            for (int j = 0;j < primary_key_set.length;j++){
                if(j != primary_key_set.length - 1 ){
                    primary_key += "`" + primary_key_set[j] + "`,";
                }else{
                    primary_key += "`" + primary_key_set[j] + "`";
                }

            }
            sql.append("PRIMARY KEY (" + primary_key + "))ENGINE = InnoDB DEFAULT CHARSET = utf8;");
        }else{
            for (int i = 0; i < columns.length;i++){
                singleAttr = "`" + columns[i] + "` VARCHAR(255) NOT NULL,";
                sql.append(singleAttr);
            }
            sql.append("PRIMARY KEY (`" + columns[0] + "`))ENGINE = InnoDB DEFAULT CHARSET = utf8;");
        }
        return sql.toString();
    }

    /**
     * 插入大批量数据的方法
     * @param sourceFilePath 大批量数据所在文件的文件路径
     * @param destinationTable 数据库中目标表的表名
     * @param splitOperator 大批量数据中每行分隔不同字段的分隔符
     * @return 返回对应插入指令的String字符串
     */
    private String insertBigData(String sourceFilePath, String destinationTable, String splitOperator){
        String sql = "LOAD DATA INFILE '" + sourceFilePath + "' IGNORE INTO TABLE " +
                destinationTable + " CHARACTER SET utf8 FIELDS TERMINATED BY '" + splitOperator + "';";
        return sql;
    }

    /**
     * 该命令用于创建user_info表上的触发器updateUser
     * @return 返回对应sql命令的字符串
     */
    private String createTriggerSQL(){
        return "CREATE TRIGGER updateUser BEFORE UPDATE ON user_info FOR EACH ROW\n" +
                "  BEGIN\n" +
                "    SET NEW.balance = NEW.balance - 3;\n" +
                "  END;";
    }

    /**
     * 增加单列的抽象方法
     * @param tableName 要增加列的表的表明
     * @param columnNames_type 由<要添加的列的列名，对应的属性>这样键值对组成的map
     * @return 返回sql命令的String字符串
     * 暂时只是简单的实现了增加单列的抽象方法
     */
    private String addColumnSQL(String tableName, Map<String, String> columnNames_type){
        String sql = "ALTER TABLE " + tableName + " ADD COLUMN ";
        for (String key : columnNames_type.keySet()) {
            sql += ("`" + key + "` " + columnNames_type.get(key));
        }
        sql += ";";
        return sql;
    }

    String sql1 = "UPDATE rent r\n" +
                "SET r.charge = if(minute(r.end_time - r.start_time) <= 30, 1,\n" +
                "                  if(minute(r.end_time - r.start_time) <= 60, 2, if(minute(r.end_time - r.start_time) <= 90, 3, 4)));";
        String sql2 = "UPDATE user u, (SELECT\n" +
                "                  r.uid,\n" +
                "                  sum(r.charge) charge\n" +
                "                FROM rent r\n" +
                "                GROUP BY r.uid) money_info\n" +
                "SET u.money = u.money - money_info.charge\n" +
                "WHERE u.uid = money_info.uid";
    /**
     * @return 第二题对应的sql命令
     */
    private String topic2SQL(){
        return "UPDATE user_info,(SELECT t2.uid,t2.dest\n" +
                "                  FROM\n" +
                "                    (SELECT dest, uid, count(*) num FROM record  WHERE hour(str_to_date(endT, '%Y/%m/%d-%H:%i:%s')) BETWEEN 18 AND 24\n" +
                "                    GROUP BY dest, uid) t2,\n" +
                "                    (select t1.uid,max(t1.num) m from\n" +
                "                      (SELECT dest, uid, count(*) num FROM record  WHERE hour(str_to_date(endT, '%Y/%m/%d-%H:%i:%s')) BETWEEN 18 AND 24\n" +
                "                      GROUP BY dest, uid) t1 GROUP BY uid) t3\n" +
                "                  WHERE t2.uid=t3.uid AND t2.num=t3.m) uid_location\n" +
                "SET location = uid_location.dest WHERE user_info.uid = uid_location.uid;";
    }

    /**
     * @return 第三题对应的sql命令
     */
    private String topic3SQL(){
        return "UPDATE record r SET r.charge = if(minute(str_to_date(r.endT, '%Y/%m/%d-%H:%i:%s') - str_to_date(r.startT, '%Y/%m/%d-%H:%i:%s')) <= 30, 1,\n" +
                "                                  if(minute(str_to_date(r.endT, '%Y/%m/%d-%H:%i:%s') - str_to_date(r.startT, '%Y/%m/%d-%H:%i:%s')) <= 60, 2,\n" +
                "                                     if(minute(str_to_date(r.endT, '%Y/%m/%d-%H:%i:%s') - str_to_date(r.startT, '%Y/%m/%d-%H:%i:%s')) <= 90, 3, 4)));";
    }

    /**
     * @return 第四题对应的sql命令
     */
    private String topic4SQL(){
        return "CREATE TABLE `repair` SELECT t1.bid, t2.lastLocation, t1.usedTime\n" +
                "                      FROM (SELECT r.bid, sum(minute(str_to_date(r.endT, '%Y/%m/%d-%H:%i:%s') - str_to_date(r.startT, '%Y/%m/%d-%H:%i:%s'))) usedTime\n" +
                "                            FROM record r\n" +
                "                            GROUP BY r.bid) t1,\n" +
                "                        (SELECT bid, dest AS lastLocation\n" +
                "                         FROM record r1\n" +
                "                         WHERE str_to_date(r1.endT, '%Y/%m/%d-%H:%i:%s') = (SELECT max(str_to_date(endT, '%Y/%m/%d-%H:%i:%s')) FROM record r2 WHERE r1.bid = r2.bid)\n" +
                "                         GROUP BY r1.bid, r1.dest) t2\n" +
                "  WHERE t1.bid = t2.bid;";
    }

    /**
     * 执行sql语句的抽象方法
     * @param sql 要执行的sql命令对应的字符串
     * @return 返回执行成功消息， -1失败，0成功
     */
    private int executeSql(String sql){
        //声明Connection对象
        Connection connection;
        //驱动程序名
        String driver = "com.mysql.cj.jdbc.Driver";
        //URL指向要访问的数据库名login
        String url = "jdbc:mysql://localhost:3306/databasedemo?useUnicode=true&characterEncoding=utf8&useSSL=false";
        //MySQL配置时的用户名
        String user = "root";
        //MySQL配置时的密码
        String password = "xiezhenyu";

        try{
            //加载驱动程序
            Class.forName(driver);
            //1.getConnection()方法，连接MySQL数据库！！
            connection = DriverManager.getConnection(url, user, password);
            if(!connection.isClosed()){
                System.out.println("Succeeded connecting to the Database!");
            }
            //2.创建statement类对象，用来执行SQL语句！！
            Statement statement = connection.createStatement();
            //要执行的SQL语句
            //3.ResultSet类，用来存放获取的结果集！！
            statement.execute(sql);
            connection.close();
            return 1;
        }catch(ClassNotFoundException e){
            //数据库驱动类异常处理
            System.out.println("Sorry, cannot find the driver!");
            e.printStackTrace();
        }catch(SQLException e){
            //数据库连接失败异常处理
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * 工具方法，去掉某个String数组里面的null值
     * @param strArray
     * @return
     */
    private String[] removeArrayEmptyTextBackNewArray(String[] strArray) {
        List<String> strList= Arrays.asList(strArray);
        List<String> strListNew=new ArrayList<String>();
        for (int i = 0; i <strList.size(); i++) {
            if (strList.get(i)!=null&&!strList.get(i).equals("")){
                strListNew.add(strList.get(i));
            }
        }
        String[] strNewArray = strListNew.toArray(new String[strListNew.size()]);
        return   strNewArray;
    }


    /**
     * 主程序入口
     * @param arg
     */
    public static void main(String[] arg) {
        MysqlHelper mysqlHelper = new MysqlHelper();
//
//        long startTime = System.currentTimeMillis();
//        String sql = mysqlHelper.getCreateTableSQL("user_info",new String[]{"uid", "uname", "phone", "balance"}, false, null);
//        mysqlHelper.executeSql(sql);
//        String insertSql = mysqlHelper.insertBigData("/tmp/user.txt", "user_info", ";");
//        mysqlHelper.executeSql(insertSql);
//        //加trigger
//        String triggerSQL = mysqlHelper.createTriggerSQL();
//        mysqlHelper.executeSql(triggerSQL);
//
//        String createSql = mysqlHelper.getCreateTableSQL("record", new String[]{"bid", "uid", "origin", "startT", "dest", "endT"}, true, new String[]{"bid","uid","startT"});
//        mysqlHelper.executeSql(createSql);
//        insertSql = mysqlHelper.insertBigData("/tmp/record.txt", "record", ";");
//        mysqlHelper.executeSql(insertSql);
//
//
//        sql = mysqlHelper.getCreateTableSQL("bike", new String[]{"bid"}, false, null);
//        mysqlHelper.executeSql(sql);
//        insertSql = mysqlHelper.insertBigData("/tmp/bike.txt", "bike", ";");
//        mysqlHelper.executeSql(insertSql);
//
//        long endTime = System.currentTimeMillis();
//        double time = (endTime - startTime)/1000;
//        System.out.println("创建user表以及插入数据执行时间为：" + time + "s");

        //第二题
        Map<String, String> map = new HashMap<String, String>();
        map.put("location","VARCHAR(255)");
        String addColumnSQL = mysqlHelper.addColumnSQL("user_info", map);
        mysqlHelper.executeSql(addColumnSQL);
        String sql2 = mysqlHelper.topic2SQL();
        mysqlHelper.executeSql(sql2);

        //第三题
        map.clear();
        map.put("charge", "VARCHAR(255)");
        addColumnSQL = mysqlHelper.addColumnSQL("record", map);
        mysqlHelper.executeSql(addColumnSQL);
        String sql3 = mysqlHelper.topic3SQL();
        mysqlHelper.executeSql(sql3);

        //第四题
        String sql4 = mysqlHelper.topic4SQL();
        mysqlHelper.executeSql(sql4);

//        mysqlHelper.saveData2DB(0, "/Users/xiezhenyu/Desktop/课件/数据库开发技术/assignments/2017/2/分配方案.xls", "ASSIGNMENTS", new String[]{"department", "sid", "sname", "ssex", "campus", "dormitory", "price_standard"}, " ");
//        mysqlHelper.saveData2DB(1,"/Users/xiezhenyu/Desktop/课件/数据库开发技术/assignments/2017/2/电话.txt","dor_phone", new String[]{"doname", "phone"}, ";");
    }
}
