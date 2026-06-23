import { get, post, put, del } from '@/utils/request.js';

export const listAddresses = () => get('/app-api/member/address/list');
export const getDefaultAddress = () => get('/app-api/member/address/get-default');
export const createAddress = (body) => post('/app-api/member/address/create', body);
export const updateAddress = (body) => put('/app-api/member/address/update', body);
export const deleteAddress = (id) => del(`/app-api/member/address/delete?id=${id}`);

// 省市区地区树（@PermitAll）：[{id,name,children:[{...children}]}]
export const getAreaTree = () => get('/app-api/system/area/tree');
