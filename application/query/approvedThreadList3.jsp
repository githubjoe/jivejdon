<%@ taglib uri="struts-logic" prefix="logic" %>
<%@ taglib uri="struts-bean" prefix="bean" %>
<%@ taglib uri="struts-html" prefix="html" %>
<%@ taglib uri="/WEB-INF/MultiPagesREST.tld" prefix="MultiPagesREST" %>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page session="false" %>
<%@ page trimDirectiveWhitespaces="true" %>
<%          
    com.jdon.jivejdon.util.ToolsUtil.setHeaderCache(60, request, response);	
%>
    <META HTTP-EQUIV="Pragma" CONTENT="no-cache">
    <META HTTP-EQUIV="Cache-Control" CONTENT="no-store">
    <META HTTP-EQUIV="Expires" CONTENT="0">   
<%
String offset = "0";
if (request.getParameter("offset")!=null){
   offset = request.getParameter("offset");
}
String count = "5";
if (request.getParameter("count")!=null){
   count = request.getParameter("count");
}
%>
<logic:iterate indexId="i" id="forumThread" name="threadListForm" property="list" offset="<%=offset%>" length="<%=count%>">
  <%@ include file="threadListCore.jsp" %>
</logic:iterate>
