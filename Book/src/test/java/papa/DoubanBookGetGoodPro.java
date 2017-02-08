package papa;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import de.ifdag.log.Log;
import papa.Queue.DBQueue;
import papa.Thread.DBInsertThread;
import papa.Thread.RedisQueryThread;
import papa.bean.BookInfoBean;
import papa.dao.ConnectionSource;
import papa.dao.SqlDao;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.proxy.SelfPorxy;
import us.codecraft.webmagic.scheduler.RedisScheduler;

/**
 * 
 * @author ReverieNight@Foxmail.com
 *
 */
public class DoubanBookGetGoodPro implements PageProcessor {

	private static Site site = Site.me().setRetryTimes(3).setSleepTime(2000).setTimeOut(7000).setCycleRetryTimes(3);

	// public static final String URL_LIST =
	// "http://www\\.36dm\\.com/sort-4-\\d+\\.html";
	// public static final String URL_POST =
	// "http://www\\.36dm\\.com/show-\\w+\\.html";

	SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
	SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM");
	SimpleDateFormat sdf3 = new SimpleDateFormat("yyyy");
	static Connection conn=null;

	// 详情页的正则表达式
	public static final String URL_POST = "https://book\\.douban\\.com/subject/.+";
	
	public static final String URL_MAIN="https://book.douban.com/tag/%E5%B0%8F%E8%AF%B4";

	public static final String[] PAINFO = { "作者", "出版社", "副标题:", "原作名", "译者", "出版年", "页数", "定价", "丛书", "装帧", "ISBN" };

	public static final String defCountry = "中国";
	
	public static final int MaxProxy=200;
	
	public static final  int selfID=11;
	

	public Site getSite() {

		return site;
	}

	public void process(Page page) {
		  
		if (page.getUrl().regex(URL_POST).match()) {
			BookInfoBean bib = new BookInfoBean();
			String url = page.getUrl().get();
			String argID[] = url.split("/");
			String doubanID = argID[argID.length - 1];
			bib.setDoubanID(doubanID);
			String bookName = page.getHtml().xpath("//span[@property=\"v:itemreviewed\"]/text()").get();
			bib.setBookName(bookName);
			String temp = page.getHtml().xpath("//div[@id=\"info\"]").get();
			String lineInfo[] = temp.split("<br>");
			String line;

			String resultInfo;
			for (int i = 0; i < lineInfo.length; i++) {

				line = lineInfo[i].replaceAll("\n", "");
				for (int j = 0; j < PAINFO.length; j++) {
					if (line.contains(PAINFO[j])) {
						resultInfo = getPaInfo(line);
						switch (j) {
						// {"作者","出版社","副标题:","原作名","译者","出版年","页数","定价","丛书","装帧","ISBN"};
						case 0:

							String arg[] = resultInfo.split("\\]");

							String arg2[] = resultInfo.split("\\)");
							if (arg.length > 1 || arg2.length > 1) {
								if (arg.length > 1) {

									bib.setZuozhe(arg[1]);
									bib.setZuozheCountry(arg[0].split("\\[")[1]);
								} else {

									bib.setZuozhe(arg2[1]);
									bib.setZuozheCountry(arg2[0].split("\\(")[1]);
								}

							} else {
								bib.setZuozhe(resultInfo);
								bib.setZuozheCountry(defCountry);

							}

							break;
						case 1:
							bib.setChubanshe(resultInfo);
							break;
						case 2:
							bib.setFubiaoti(resultInfo);
							break;
						case 3:
							bib.setYuanzhuoming(resultInfo);
							break;
						case 4:
							bib.setYizhe(resultInfo);
							break;
						case 5:
							try {
								Date d = new Date(sdf1.parse(resultInfo).getTime());
								bib.setChubannian(d);
							} catch (Exception e) {

								try {
									Date d = new Date(sdf2.parse(resultInfo).getTime());
									bib.setChubannian(d);
								} catch (Exception e2) {

									try {
										Date d = new Date(sdf3.parse(resultInfo).getTime());
										bib.setChubannian(d);
									} catch (Exception e3) {
										Log.error(doubanID + " date error");
									}

								}

							}

							break;
						case 6:
							try {
								bib.setPageCount(Integer.parseInt(resultInfo));
							} catch (Exception e) {

							}
							break;
						case 7:
							try {
								bib.setPrice(Double.parseDouble(resultInfo));
							} catch (Exception e) {

							}
							break;
						case 8:
							bib.setCongshu(resultInfo);
							break;
						case 9:
							bib.setZhuangzhen(resultInfo);
							break;
						case 10:
							bib.setIsbn(resultInfo);
							break;
						default:
							break;
						}
					}
				}

			}

			List<String> jianjie = page.getHtml().xpath("//div[@id=\"link-report\"]//div[@class=\"intro\"]/p/text()")
					.all();
			String jj = "";
			for (int i = 0; i < jianjie.size(); i++) {
				jj = jj + jianjie.get(i);

			}
			bib.setSynopsis(jj);

			List<String> tags = page.getHtml().xpath("//div[@id=\"db-tags-section\"]//div[@class=\"indent\"]//a/text()")
					.all();
			String bookTags = "";
			for (int i = 0; i < tags.size(); i++) {
				if (i != 0) {
					bookTags = bookTags + "#";
				}
				bookTags = bookTags + tags.get(i);
			}
			bib.setTags(bookTags);
			String douBanScore = page.getHtml()
					.xpath("//div[@id=\"interest_sectl\"]//strong[@class=\"ll rating_num \"]/text()").get();

			String douBanScorePerSonCount = page.getHtml()
					.xpath("//div[@id=\"interest_sectl\"]//span[@property=\"v:votes \"]/text()").get();

			try {

				bib.setDoubanScore(Double.parseDouble(douBanScore));
			} catch (Exception e) {

			}
			try {
				bib.setDoubanScorePersonCount(Integer.parseInt(douBanScorePerSonCount));
			} catch (Exception e) {

			}
			DBQueue.put(bib);
			// System.out.println(bib.toString());
		} else {
			Log.error("error url " + page.getUrl().toString());

		}
	}

	
	public static void setProxyPool(){
		
		 List<String[]> poolHosts = new ArrayList<String[]>();
		
			try {
				conn = ConnectionSource.getConnection();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			SqlDao dao =new SqlDao(conn);
		    
			List<String []>list =dao.getporxyList(selfID);
			
			if(list.size()<MaxProxy){
		    	dao.updateProxyIP(selfID, 0);
		    	list.addAll(dao.getporxyList(selfID));
			}
		
			for(String [] s:  list){
				  poolHosts.add(new String[]{"","",s[0],s[1]});
			}
			
		
			
			site.setHttpProxyPool(new SelfPorxy(poolHosts, false));
		 

		
	}
	
	public static void main(String[] args) {
		 
		setProxyPool();
		new DBInsertThread().start();
		RedisScheduler rs  = new RedisScheduler("127.0.0.1");

		Spider sp =Spider.create(new DoubanBookGetGoodPro())
			.addUrl("https://book.douban.com/tag/%E5%B0%8F%E8%AF%B4")	//开始地址	
			.thread(10)	
			//.scheduler(new FileCacheQueueScheduler("/webmagic/book/20170204/cache/"))
			.setScheduler(rs);
		
		sp.run();
		
		
	}

	public static String getPaInfo(String str) {

		String temp = str.split("</span>")[1];
		String args[] = temp.split("</a>");
		if (args.length > 1) {
			return args[0].split(">")[1];

		} else {
			return temp.trim();
		}

	}

}