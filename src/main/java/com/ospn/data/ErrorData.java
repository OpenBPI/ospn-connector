package com.ospn.data;

public class ErrorData {
    public String errCode;
    public String errInfo;

    public ErrorData(String errCode, String errInfo){
        this.errCode = errCode;
        this.errInfo = errInfo;
    }

    @Override
    public String toString() {
        return errCode+":"+errInfo;
    }
}
