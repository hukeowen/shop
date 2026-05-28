<%@ page language="java" contentType="text/html; charset=utf-8"
    pageEncoding="utf-8"%>
 <%
//服务器
String path = request.getContextPath();
String scheme = request.getHeader("X-Forwarded-Scheme") ;
String basePath =scheme+"://"+request.getServerName()+path+"/";
if(scheme==null || scheme.equals("")){
	scheme=request.getScheme();
	basePath =scheme+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
}

request.setAttribute("qrurl",basePath+"pay.html");
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html lang="zh-cmn-Hans"> 
<head>
<meta charset='utf-8'>
<meta name="author" content="xianxm, xianxm@allinpay.com"/>
<meta name="viewport" content="initial-scale=1, maximum-scale=3, minimum-scale=1, user-scalable=no">
<meta name="format-detection" content="telephone=no"/>
<meta name="format-detection" content="email=no"/>
<script src="js/jquery.min.js"></script>
<script src="js/jquery.qrcode.min.js"></script>
<script src="js/fastclick.js"></script>
<title>微信/支付宝钱包扫一扫</title>
</head>
<body >
<center>
<b>二维码链接</b>:<%= basePath %>pay.html<br/><br/><br/>
 <div class="msgPC__hd">
            
 </div>
 <br/><br/><br/>
 请使用微信/支付宝钱包扫一扫二维码
</center>
</body>
<script type="text/javascript">
$('.msgPC__hd').qrcode({
    /* render: "table", //table方式  */
     width: 240, //宽度 
     height:240, //高度 
     text: "${qrurl}", //任意内容 
     background :"#ffffff",//背景颜色  
     foreground :"#000000" //前景颜色  
 });
		$("#msg_PC").show();
</script>
</html>