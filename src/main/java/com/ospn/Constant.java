package com.ospn;

import com.ospn.data.ErrorData;

public interface Constant {
    int FriendStatus_Wait = 0;
    int FriendStatus_Normal = 1;
    int FriendStatus_Delete = 2;
    int FriendStatus_Blacked = 3;

    int MemberStatus_Wait = 0;
    int MemberStatus_Normal = 1;
    int MemberStatus_Owner = 2;
    int MemberStatus_Admin = 3;
    int MemberStatus_Mute = 4;
    int MemberStatus_Deleted = -1;

    int ReceiptStatus_Wait = 0;
    int ReceiptStatus_Error = 1;
    int ReceiptStatus_Complete = 2;

    int MessageStatus_Saved = 0;
    int MessageStatus_Readed = 1;

    int RequestStatus_Wait = 0;
    int RequestStatus_Agree = 2;
    int RequestStatus_Reject = 3;

    int LoginState_Wait = 0;
    int LoginState_Challenge = 1;
    int LoginState_Finish = 2;

    ErrorData E_exception = new ErrorData("10000","exception");
    ErrorData E_noLogin = new ErrorData("10001","user no login");
    ErrorData E_needLogin = new ErrorData("10002","need login");
    ErrorData E_timeSync = new ErrorData("10003","time differ too big");
    ErrorData E_hashVerify = new ErrorData("100004","hash verify failed");
    ErrorData E_signVerify = new ErrorData("100005","sign verify failed");
    ErrorData E_needCrypto = new ErrorData("10006","need crypto info");
    ErrorData E_userNoFind = new ErrorData("10007","user no found");
    ErrorData E_groupNoFind = new ErrorData("10008","group no found");
    ErrorData E_friendNoFind = new ErrorData("10009","friend no found");
    ErrorData E_memberNoFind = new ErrorData("10010","member no found");
    ErrorData E_stateError = new ErrorData("10011","state error");
    ErrorData E_verifyError = new ErrorData("10012","veirfy error");
    ErrorData E_dataOverrun = new ErrorData("10013","data overrun");
    ErrorData E_dataNoFind = new ErrorData("10014","data no found");
    ErrorData E_maxLimits = new ErrorData("10015","max limits");
    ErrorData E_missData = new ErrorData("10020","missing data");
    ErrorData E_userExist = new ErrorData("10021","user already exist");
    ErrorData E_registFailed = new ErrorData("10022","register failed");
    ErrorData E_dataBase = new ErrorData("10023","database error");
    ErrorData E_requestNoFind = new ErrorData("10024","request no found");
    ErrorData E_userError = new ErrorData("10025","userID error/null");
    ErrorData E_alreadyFriend = new ErrorData("10026","already friend");
    ErrorData E_alreadyMember = new ErrorData("10027","already member");
    ErrorData E_noRight = new ErrorData("10028","no right");
    ErrorData E_errorCmd = new ErrorData("10029","unknown command");
    ErrorData E_cryptError = new ErrorData("10030","crypto failed");
}
