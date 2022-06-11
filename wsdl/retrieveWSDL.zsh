#!/usr/bin/zsh

wget "http://ryleh/cws/ContentService.svc?wsdl" -O ContentService.wsdl
wget "http://ryleh/cws/Authentication.svc?wsdl" -O Authentication.wsdl
wget "http://ryleh/cws/DocumentManagement.svc?wsdl" -O DocumentManagement.wsdl
wget "http://ryleh/cws/AdminService.svc?wsdl" -O AdminService.wsdl
