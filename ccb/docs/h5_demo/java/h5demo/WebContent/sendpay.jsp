<%@page import="com.allinpay.syb.lib.SybUtil"%>
<%@page import="com.allinpay.syb.lib.SybConstants"%>
<%@page import="java.util.TreeMap"%>
<%@ page language="java" contentType="text/html; charset=utf-8"%>
<%! String formatString(String text) {
		return text == null ? "" : text.trim();
	}
%>
<%
	request.setCharacterEncoding("utf-8");

	String appid = SybConstants.SYB_APPID;
	String cusid = SybConstants.SYB_CUSID;
	String version = SybConstants.VERSION;
	String charset = "utf-8";
	String trxamt   = formatString(request.getParameter("trxamt"));
	String reqsn  = formatString(request.getParameter("reqsn"));
	String returl = SybConstants.RET_URL;
	String notify_url = SybConstants.NOTFIY_URL;
	String body = formatString(request.getParameter("body"));
	String remark = formatString(request.getParameter("remark"));
	String randomstr = String.valueOf(System.currentTimeMillis()) ;
	String validtime = String.valueOf( SybConstants.VALID_TIME);
	String signtype = SybConstants.SIGN_TYPE;
	TreeMap<String,String> params = new TreeMap<String,String>();
	if(!SybUtil.isEmpty(SybConstants.SYB_ORGID))
		params.put("orgid", SybConstants.SYB_ORGID);
	params.put("appid", appid);
	params.put("cusid", cusid);
	params.put("version", version);
	params.put("charset", charset);
	params.put("trxamt", trxamt);
	params.put("reqsn", reqsn);
	params.put("returl", returl);
	params.put("notify_url", notify_url);
	params.put("body", body);
	params.put("randomstr", randomstr);
	params.put("remark", remark);
	params.put("validtime", validtime);

	params.put("signtype",signtype );
	String appkey = "";
	if(SybConstants.SIGN_TYPE.equals("RSA"))
		appkey = SybConstants.SYB_RSACUSPRIKEY;
	else if(SybConstants.SIGN_TYPE.equals("SM2"))
		appkey = SybConstants.SYB_SM2PPRIVATEKEY;
	else 
		appkey = SybConstants.SYB_MD5_APPKEY;
	String sign  = SybUtil.unionSign(params,appkey,SybConstants.SIGN_TYPE);
	System.out.println(sign);
%>
<html>
	<head>
		<title>跳转
		</title>
	</head>
	<body   onload="document.gatewayForm.submit()">
		<form name="gatewayForm" action='<%=SybConstants.SYB_APIURL+"/h5unionpay/unionorder"%>' method='POST'>
			<input type="hidden" name="appid" id="appid" value="<%=appid %>" />
       		<input type="hidden" name="cusid" id="cusid" value="<%=cusid%>" />
       		<input type="hidden" name="version" id="version" value="<%=version%>" />
       		<input type="hidden" name="charset" id="charset" value="<%=charset%>" />
       		<input type="hidden" name="trxamt" id="trxamt" value="<%=trxamt%>" />
       		<input type="hidden" name="reqsn" id="reqsn" value="<%=reqsn%>" />
       		<input type="hidden" name="randomstr" id="randomstr" value="<%=randomstr%>" />
       		<input type="hidden" name="body" id="body" value="<%=body%>" />
       		<input type="hidden" name="validtime" id="validtime" value="<%=validtime%>" />
       		<input type="hidden" name="remark" id="remark" value="<%=remark%>" />
       		<input type="hidden" name="returl" id="returl" value="<%=returl %>" />
       		<input type="hidden" name="notify_url" id="notify_url" value="<%=notify_url%>" />
       		<input type="hidden" name="signtype" id="signtype" value="<%=signtype%>" />
       		<input type="hidden" name="sign" id="sign" value="<%=sign%>" />
		</form>
	</body>
</html>
