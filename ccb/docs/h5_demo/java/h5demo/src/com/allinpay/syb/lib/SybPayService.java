package com.allinpay.syb.lib;

import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;

public class SybPayService {
	
	public Map<String,String> cancel(long trxamt,String reqsn,String oldtrxid,String oldreqsn) throws Exception{
		HttpConnectionUtil http = new HttpConnectionUtil(SybConstants.SYB_APIURL+"/unitorder/cancel");
		http.init();
		TreeMap<String,String> params = new TreeMap<String,String>();
		if(!SybUtil.isEmpty(SybConstants.SYB_ORGID))
			params.put("orgid", SybConstants.SYB_ORGID);
		params.put("cusid", SybConstants.SYB_CUSID);
		params.put("appid", SybConstants.SYB_APPID);
		params.put("version", "11");
		params.put("trxamt", String.valueOf(trxamt));
		params.put("reqsn", reqsn);
		params.put("oldtrxid", oldtrxid);
		params.put("oldreqsn", oldreqsn);
		params.put("randomstr", SybUtil.getValidatecode(8));
		params.put("signtype", SybConstants.SIGN_TYPE);
		String appkey = "";
		if(SybConstants.SIGN_TYPE.equals("RSA"))
			appkey = SybConstants.SYB_RSACUSPRIKEY;
		else if(SybConstants.SIGN_TYPE.equals("SM2"))
			appkey = SybConstants.SYB_SM2PPRIVATEKEY;
		else 
			appkey = SybConstants.SYB_MD5_APPKEY;
		params.put("sign", SybUtil.unionSign(params,appkey,SybConstants.SIGN_TYPE));
		byte[] bys = http.postParams(params, true);
		String result = new String(bys,"UTF-8");
		Map<String,String> map = handleResult(result);
		return map;
	}
	
	public Map<String,String> refund(long trxamt,String reqsn,String oldtrxid,String oldreqsn) throws Exception{
		HttpConnectionUtil http = new HttpConnectionUtil(SybConstants.SYB_APIURL+"/unitorder/refund");
		http.init();
		TreeMap<String,String> params = new TreeMap<String,String>();
		if(!SybUtil.isEmpty(SybConstants.SYB_ORGID))
			params.put("orgid", SybConstants.SYB_ORGID);
		params.put("cusid", SybConstants.SYB_CUSID);
		params.put("appid", SybConstants.SYB_APPID);
		params.put("version", "11");
		params.put("trxamt", String.valueOf(trxamt));
		params.put("reqsn", reqsn);
		params.put("oldreqsn", oldreqsn);
		params.put("oldtrxid", oldtrxid);
		params.put("randomstr", SybUtil.getValidatecode(8));
		params.put("signtype", SybConstants.SIGN_TYPE);
		String appkey = "";
		if(SybConstants.SIGN_TYPE.equals("RSA"))
			appkey = SybConstants.SYB_RSACUSPRIKEY;
		else if(SybConstants.SIGN_TYPE.equals("SM2"))
			appkey = SybConstants.SYB_SM2PPRIVATEKEY;
		else 
			appkey = SybConstants.SYB_MD5_APPKEY;
		params.put("sign", SybUtil.unionSign(params,appkey,SybConstants.SIGN_TYPE));
		byte[] bys = http.postParams(params, true);
		String result = new String(bys,"UTF-8");
		Map<String,String> map = handleResult(result);
		return map;
	}
	
	public Map<String,String> query(String reqsn,String trxid) throws Exception{
		HttpConnectionUtil http = new HttpConnectionUtil(SybConstants.SYB_APIURL+"/unitorder/query");
		http.init();
		TreeMap<String,String> params = new TreeMap<String,String>();
		if(!SybUtil.isEmpty(SybConstants.SYB_ORGID))
			params.put("orgid", SybConstants.SYB_ORGID);
		params.put("cusid", SybConstants.SYB_CUSID);
		params.put("appid", SybConstants.SYB_APPID);
		params.put("version", "11");
		params.put("reqsn", reqsn);
		params.put("trxid", trxid);
		params.put("randomstr", SybUtil.getValidatecode(8));
		params.put("signtype", SybConstants.SIGN_TYPE);
		String appkey = "";
		if(SybConstants.SIGN_TYPE.equals("RSA"))
			appkey = SybConstants.SYB_RSACUSPRIKEY;
		else if(SybConstants.SIGN_TYPE.equals("SM2"))
			appkey = SybConstants.SYB_SM2PPRIVATEKEY;
		else 
			appkey = SybConstants.SYB_MD5_APPKEY;
		params.put("sign", SybUtil.unionSign(params,appkey,SybConstants.SIGN_TYPE));
		byte[] bys = http.postParams(params, true);
		String result = new String(bys,"UTF-8");
		Map<String,String> map = handleResult(result);
		return map;
	}
	
	
	public static Map<String,String> handleResult(String result) throws Exception{
		System.out.println("ret:"+result);
		Map map = SybUtil.json2Obj(result, Map.class);
		if(map == null){
			throw new Exception("返回数据错误");
		}
		if("SUCCESS".equals(map.get("retcode"))){
			TreeMap tmap = new TreeMap();
			tmap.putAll(map);
			String appkey = "";
			if(SybConstants.SIGN_TYPE.equals("RSA"))
				appkey = SybConstants.SYB_RSATLPUBKEY;
			else if(SybConstants.SIGN_TYPE.equals("SM2"))
				appkey = SybConstants.SYB_SM2TLPUBKEY;
			else 
				appkey = SybConstants.SYB_MD5_APPKEY;
			if(SybUtil.validSign(tmap, appkey, SybConstants.SIGN_TYPE)){
				System.out.println("签名成功");
				return map;
			}else{
				throw new Exception("验证签名失败");
			}
			
		}else{
			throw new Exception(map.get("retmsg").toString());
		}
	}
	
	
}
